package org.elder.sourcerer.eventstoredb;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.Position;
import com.eventstore.dbclient.ReadResult;
import com.eventstore.dbclient.RecordedEvent;
import com.eventstore.dbclient.ResolvedEvent;
import com.eventstore.dbclient.StreamNotFoundException;
import com.eventstore.dbclient.StreamRevision;
import com.eventstore.dbclient.SubscribeToStreamOptions;
import com.eventstore.dbclient.Subscription;
import com.eventstore.dbclient.SubscriptionListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.collect.ImmutableMap;
import org.elder.sourcerer.EventReadResult;
import org.elder.sourcerer.EventSubscriptionUpdate;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EventStoreGrpcEventRepositoryTest {
    public static class Event {
        private int integer;
        private String str;

        public int getInteger() {
            return integer;
        }

        public void setInteger(final int integer) {
            this.integer = integer;
        }

        public String getStr() {
            return str;
        }

        public void setStr(final String str) {
            this.str = str;
        }
    }

    // NOTE: This has to be populated as EventStore client expects them to be present and in
    // certain format, the keys are in constants internal to the client
    private static final Map<String, String> SYSTEM_METADATA = ImmutableMap.of(
            "content-type", "application/json",
            "created", Long.toString(Instant.now().toEpochMilli()),
            "is-json", "true",
            "type", "type");
    private static final Position RANDOM_POSITION = new Position(23423, 23420);

    private EventStoreDBClient eventStore;
    private ObjectMapper objectMapper;
    private ObjectReader reader;
    private EventStoreGrpcEventRepository<Event> repository;

    @Before
    public void setUp() {
        eventStore = mock(EventStoreDBClient.class);
        objectMapper = mock(ObjectMapper.class);
        reader = mock(ObjectReader.class);
        when(objectMapper.readerFor(any(Class.class))).thenReturn(reader);
        repository = new EventStoreGrpcEventRepository<>(
                "pref",
                eventStore,
                Event.class,
                objectMapper,
                null);
    }

    @Test
    public void readReturnsNullOnNotFound() {
        when(eventStore.readStream(anyString(), anyLong(), any()))
                .then((invocation) -> {
                    CompletableFuture<ReadResult> future = new CompletableFuture<>();
                    future.completeExceptionally(new StreamNotFoundException());
                    return future;
                });
        EventReadResult<Event> response = repository.read("stream");
        Assert.assertNull(response);
    }

    @Test
    public void errorPropagatedFromEsjc() throws IOException {
        String streamId = "test-stream";

        when(reader.readValue((byte[]) any())).thenReturn(new Object());
        // Subscribe call not yet mocked, ensures we don't call subscribe until we subscribe
        // to the Flux
        Flux<EventSubscriptionUpdate<Event>> publisher = Flux.from(repository.getStreamPublisher(
                streamId,
                null));

        // Set up subscription - should trigger a call to underlying subscribe
        // Set up subscription - should trigger a call to underlying subscribe
        Subscription subscription = mock(Subscription.class);
        when(eventStore.subscribeToStream(
                anyString(),
                any(SubscriptionListener.class),
                any(SubscribeToStreamOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(subscription));

        // Hook up fake listener, checking that we're getting notified
        AtomicInteger seenEvents = new AtomicInteger(0);
        AtomicReference<Throwable> seenError = new AtomicReference<>(null);
        AtomicBoolean seenStop = new AtomicBoolean(false);
        publisher.limitRate(100).subscribe(
                event -> seenEvents.incrementAndGet(),
                seenError::set,
                () -> seenStop.set(true));

        ArgumentCaptor<SubscriptionListener> listenerCaptor =
                ArgumentCaptor.forClass(SubscriptionListener.class);

        verify(eventStore, times(1)).subscribeToStream(
                eq("pref-" + streamId),
                listenerCaptor.capture(),
                any(SubscribeToStreamOptions.class));

        SubscriptionListener listener = listenerCaptor.getValue();
        listener.onError(subscription, new RuntimeException("bad things happen"));

        Assert.assertNotNull(seenError.get());
        Assert.assertFalse(seenStop.get());
    }

    @Test
    public void subscriptionCallbackAppliesBackpressure() throws IOException {
        // The ESJC client applies backpressure implicitly by blocking the callback, we need to
        // ensure that the use of Reactor doesn't introduce any issues with multiple threads
        // preventing this behavior
        String streamId = "test-stream";

        when(reader.readValue((byte[]) any())).thenReturn(new Object());
        // Subscribe call not yet mocked, ensures we don't call subscribe until we subscribe
        // to the Flux
        Flux<EventSubscriptionUpdate<Event>> publisher = Flux.from(repository.getStreamPublisher(
                streamId,
                null));

        // Set up subscription - should trigger a call to underlying subscribe
        // Set up subscription - should trigger a call to underlying subscribe
        Subscription subscription = mock(Subscription.class);
        when(eventStore.subscribeToStream(
                anyString(),
                any(SubscriptionListener.class),
                any(SubscribeToStreamOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(subscription));

        // Hook up fake listener, checking that we're getting notified
        AtomicInteger seenEvents = new AtomicInteger(0);

        publisher.limitRate(100).subscribe(event -> {
            seenEvents.incrementAndGet();
        });

        ArgumentCaptor<SubscriptionListener> listenerCaptor =
                ArgumentCaptor.forClass(SubscriptionListener.class);

        verify(eventStore, times(1)).subscribeToStream(
                eq("pref-" + streamId),
                listenerCaptor.capture(),
                any(SubscribeToStreamOptions.class));

        SubscriptionListener listener = listenerCaptor.getValue();
        Assert.assertEquals(0, seenEvents.get());
        listener.onEvent(
                subscription,
                new ResolvedEvent(
                        // Event in projection (pointing to real event)
                        new RecordedEvent(
                                "$ce-pref",
                                new StreamRevision(0),
                                UUID.randomUUID(),
                                RANDOM_POSITION,
                                SYSTEM_METADATA,
                                new byte[0],
                                new byte[0]
                        ),
                        // Link event (original event pointed to)
                        new RecordedEvent(
                                "ref-123",
                                new StreamRevision(0),
                                UUID.randomUUID(),
                                RANDOM_POSITION,
                                SYSTEM_METADATA,
                                "{}".getBytes(),
                                "{}".getBytes()
                        )));
        Assert.assertEquals(1, seenEvents.get());
    }

    @Test
    public void errorPropagatedFromEsjcInCategoryPublisher() throws IOException {
        when(reader.readValue((byte[]) any())).thenReturn(new Object());
        // Subscribe call not yet mocked, ensures we don't call subscribe until we subscribe
        // to the Flux
        Flux<EventSubscriptionUpdate<Event>> publisher = Flux.from(repository.getPublisher(null));

        // Set up subscription - should trigger a call to underlying subscribe
        Subscription subscription = mock(Subscription.class);
        when(eventStore.subscribeToStream(
                anyString(),
                any(SubscriptionListener.class),
                any(SubscribeToStreamOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(subscription));

        // Hook up fake listener, checking that we're getting notified
        AtomicInteger seenEvents = new AtomicInteger(0);
        AtomicReference<Throwable> seenError = new AtomicReference<>(null);
        AtomicBoolean seenStop = new AtomicBoolean(false);
        publisher.limitRate(100).subscribe(
                event -> seenEvents.incrementAndGet(),
                seenError::set,
                () -> seenStop.set(true));

        ArgumentCaptor<SubscriptionListener> listenerCaptor =
                ArgumentCaptor.forClass(SubscriptionListener.class);

        verify(eventStore, times(1)).subscribeToStream(
                eq("$ce-pref"),
                listenerCaptor.capture(),
                any(SubscribeToStreamOptions.class));

        SubscriptionListener listener = listenerCaptor.getValue();
        listener.onError(subscription, new RuntimeException("bad things happen"));

        Assert.assertNotNull(seenError.get());
        Assert.assertFalse(seenStop.get());
    }

    @Test
    public void subscriptionCallbackAppliesBackpressureInCategoryPublisher() throws IOException {
        // The ESJC client applies backpressure implicitly by blocking the callback, we need to
        // ensure that the use of Reactor doesn't introduce any issues with multiple threads
        // preventing this behavior
        when(reader.readValue((byte[]) any())).thenReturn(new Object());
        // Subscribe call not yet mocked, ensures we don't call subscribe until we subscribe
        // to the Flux
        Flux<EventSubscriptionUpdate<Event>> publisher = Flux.from(repository.getPublisher(null));

        // Set up subscription - should trigger a call to underlying subscribe
        Subscription subscription = mock(Subscription.class);
        when(eventStore.subscribeToStream(
                anyString(),
                any(SubscriptionListener.class),
                any(SubscribeToStreamOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(subscription));

        // Hook up fake listener, checking that we're getting notified
        AtomicInteger seenEvents = new AtomicInteger(0);
        publisher
                .limitRate(100)
                .subscribe(event -> {
                    seenEvents.incrementAndGet();
                });

        ArgumentCaptor<SubscriptionListener> listenerCaptor =
                ArgumentCaptor.forClass(SubscriptionListener.class);

        verify(eventStore, times(1)).subscribeToStream(
                eq("$ce-pref"),
                listenerCaptor.capture(),
                any(SubscribeToStreamOptions.class));

        SubscriptionListener listener = listenerCaptor.getValue();
        Assert.assertEquals(0, seenEvents.get());
        listener.onEvent(
                subscription,
                new ResolvedEvent(
                        // Event in projection (pointing to real event)
                        new RecordedEvent(
                                "$ce-pref",
                                new StreamRevision(0),
                                UUID.randomUUID(),
                                RANDOM_POSITION,
                                SYSTEM_METADATA,
                                new byte[0],
                                new byte[0]
                        ),
                        // Link event (original event pointed to)
                        new RecordedEvent(
                                "ref-123",
                                new StreamRevision(0),
                                UUID.randomUUID(),
                                RANDOM_POSITION,
                                SYSTEM_METADATA,
                                "{}".getBytes(),
                                "{}".getBytes()
                        )
                )
        );
        Assert.assertEquals(1, seenEvents.get());
    }
}
