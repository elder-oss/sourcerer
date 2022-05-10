package org.elder.sourcerer.esjc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.github.msemys.esjc.CatchUpSubscription;
import com.github.msemys.esjc.CatchUpSubscriptionListener;
import com.github.msemys.esjc.CatchUpSubscriptionSettings;
import com.github.msemys.esjc.EventStore;
import com.github.msemys.esjc.ReadDirection;
import com.github.msemys.esjc.ResolvedEvent;
import com.github.msemys.esjc.SliceReadStatus;
import com.github.msemys.esjc.StreamEventsSlice;
import com.github.msemys.esjc.SubscriptionDropReason;
import com.github.msemys.esjc.UserCredentials;
import com.github.msemys.esjc.proto.EventStoreClientMessages;
import com.github.msemys.esjc.util.UUIDConverter;
import com.google.protobuf.ByteString;
import org.elder.sourcerer.EventReadResult;
import org.elder.sourcerer.EventSubscriptionUpdate;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EventStoreEsjcEventRepositoryTest {
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

    private EventStore eventStore;
    private ObjectMapper objectMapper;
    private ObjectReader reader;
    private EventStoreEsjcEventRepository<Event> repository;

    @Before
    public void setUp() {
        eventStore = mock(EventStore.class);
        objectMapper = mock(ObjectMapper.class);
        reader = mock(ObjectReader.class);
        when(objectMapper.readerFor(any(Class.class))).thenReturn(reader);
        repository = new EventStoreEsjcEventRepository<>(
                "pref",
                eventStore,
                Event.class,
                objectMapper,
                null);
    }

    @Test
    public void readReturnsNullOnDeleted() {
        when(eventStore.readStreamEventsForward(any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(wrapFuture(new StreamEventsSlice(
                        SliceReadStatus.StreamDeleted,
                        "stream",
                        0,
                        ReadDirection.Forward,
                        null,
                        1,
                        0,
                        false)));
        EventReadResult<Event> response = repository.read("stream");
        Assert.assertNull(response);
    }

    @Test
    public void readReturnsNullOnNotFound() {
        when(eventStore.readStreamEventsForward(any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(wrapFuture(new StreamEventsSlice(
                        SliceReadStatus.StreamNotFound,
                        "stream",
                        0,
                        ReadDirection.Forward,
                        null,
                        1,
                        0,
                        false)));
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
        CatchUpSubscription catchUpSubscription = mock(CatchUpSubscription.class);
        when(eventStore.subscribeToStreamFrom(
                anyString(),
                any(Long.class),
                any(CatchUpSubscriptionSettings.class),
                any(CatchUpSubscriptionListener.class)))
                .thenReturn(catchUpSubscription);

        // Hook up fake listener, checking that we're getting notified
        AtomicInteger seenEvents = new AtomicInteger(0);
        AtomicReference<Throwable> seenError = new AtomicReference<>(null);
        AtomicBoolean seenStop = new AtomicBoolean(false);
        publisher.limitRate(100).subscribe(
                event -> seenEvents.incrementAndGet(),
                seenError::set,
                () -> seenStop.set(true));

        ArgumentCaptor<CatchUpSubscriptionListener> listenerCaptor =
                ArgumentCaptor.forClass(CatchUpSubscriptionListener.class);

        verify(eventStore, times(1)).subscribeToStreamFrom(
                eq("pref-" + streamId),
                eq(null),
                any(CatchUpSubscriptionSettings.class),
                listenerCaptor.capture());

        CatchUpSubscriptionListener listener = listenerCaptor.getValue();
        listener.onClose(
                catchUpSubscription,
                SubscriptionDropReason.CatchUpError,
                new RuntimeException("bad things happen"));

        Assert.assertNotNull(seenError.get());
        Assert.assertFalse(seenStop.get());
    }

    /**
     * According to comments below, this ensures that the driver works sensibly even when
     * we block the event handler, though it's not clear from the code how this is being
     * sensible checked. Tests is kept for now, but look to make sense of why this is and
     * what it's for!
     */
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
        CatchUpSubscription catchUpSubscription = mock(CatchUpSubscription.class);
        when(eventStore.subscribeToStreamFrom(
                anyString(),
                any(Long.class),
                any(CatchUpSubscriptionSettings.class),
                any(CatchUpSubscriptionListener.class),
                any(UserCredentials.class)))
                .thenReturn(catchUpSubscription);

        // Hook up fake listener, checking that we're getting notified
        AtomicInteger seenEvents = new AtomicInteger(0);

        publisher.limitRate(100).subscribe(event -> {
            seenEvents.incrementAndGet();
        });

        ArgumentCaptor<CatchUpSubscriptionListener> listenerCaptor =
                ArgumentCaptor.forClass(CatchUpSubscriptionListener.class);

        verify(eventStore, times(1)).subscribeToStreamFrom(
                eq("pref-" + streamId),
                eq(null),
                any(CatchUpSubscriptionSettings.class),
                listenerCaptor.capture());

        CatchUpSubscriptionListener listener = listenerCaptor.getValue();
        Assert.assertEquals(0, seenEvents.get());
        listener.onEvent(
                catchUpSubscription,
                new ResolvedEvent(
                        EventStoreClientMessages
                                .ResolvedEvent
                                .newBuilder()
                                .setEvent(EventStoreClientMessages.EventRecord.newBuilder()
                                        .setCreatedEpoch(2342354)
                                        .setEventStreamId("stream")
                                        .setEventId(ByteString.copyFrom(
                                                UUIDConverter.toBytes(UUID.randomUUID())))
                                        .setEventNumber(42)
                                        .setEventType("type")
                                        .setDataContentType(0)
                                        .setMetadataContentType(0)
                                        .setData(ByteString.copyFrom("{}".getBytes())))
                                .setCommitPosition(0)
                                .setPreparePosition(0)
                                .build()));
        Assert.assertEquals(1, seenEvents.get());
    }

    @Test
    public void errorPropagatedFromEsjcInCategoryPublisher() throws IOException {
        when(reader.readValue((byte[]) any())).thenReturn(new Object());
        // Subscribe call not yet mocked, ensures we don't call subscribe until we subscribe
        // to the Flux
        Flux<EventSubscriptionUpdate<Event>> publisher = Flux.from(repository.getPublisher(null));

        // Set up subscription - should trigger a call to underlying subscribe
        CatchUpSubscription catchUpSubscription = mock(CatchUpSubscription.class);
        when(eventStore.subscribeToStreamFrom(
                anyString(),
                any(Long.class),
                any(CatchUpSubscriptionSettings.class),
                any(CatchUpSubscriptionListener.class)))
                .thenReturn(catchUpSubscription);

        // Hook up fake listener, checking that we're getting notified
        AtomicInteger seenEvents = new AtomicInteger(0);
        AtomicReference<Throwable> seenError = new AtomicReference<>(null);
        AtomicBoolean seenStop = new AtomicBoolean(false);
        publisher.limitRate(100).subscribe(
                event -> seenEvents.incrementAndGet(),
                seenError::set,
                () -> seenStop.set(true));

        ArgumentCaptor<CatchUpSubscriptionListener> listenerCaptor =
                ArgumentCaptor.forClass(CatchUpSubscriptionListener.class);

        verify(eventStore, times(1)).subscribeToStreamFrom(
                eq("$ce-pref"),
                eq(null),
                any(CatchUpSubscriptionSettings.class),
                listenerCaptor.capture());

        CatchUpSubscriptionListener listener = listenerCaptor.getValue();
        listener.onClose(
                catchUpSubscription,
                SubscriptionDropReason.CatchUpError,
                new RuntimeException("bad things happen"));

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
        CatchUpSubscription catchUpSubscription = mock(CatchUpSubscription.class);
        when(eventStore.subscribeToStreamFrom(
                anyString(),
                any(Long.class),
                any(CatchUpSubscriptionSettings.class),
                any(CatchUpSubscriptionListener.class),
                any(UserCredentials.class)))
                .thenReturn(catchUpSubscription);

        // Hook up fake listener, checking that we're getting notified
        AtomicInteger seenEvents = new AtomicInteger(0);
        publisher
                .limitRate(100)
                .subscribe(event -> {
                    seenEvents.incrementAndGet();
                });

        ArgumentCaptor<CatchUpSubscriptionListener> listenerCaptor =
                ArgumentCaptor.forClass(CatchUpSubscriptionListener.class);

        verify(eventStore, times(1)).subscribeToStreamFrom(
                eq("$ce-pref"),
                eq(null),
                any(CatchUpSubscriptionSettings.class),
                listenerCaptor.capture());

        CatchUpSubscriptionListener listener = listenerCaptor.getValue();
        Assert.assertEquals(0, seenEvents.get());
        listener.onEvent(
                catchUpSubscription,
                new ResolvedEvent(
                        EventStoreClientMessages
                                .ResolvedEvent
                                .newBuilder()
                                .setEvent(EventStoreClientMessages.EventRecord.newBuilder()
                                        .setCreatedEpoch(2342354)
                                        .setEventStreamId("stream")
                                        .setEventId(ByteString.copyFrom(
                                                UUIDConverter.toBytes(UUID.randomUUID())))
                                        .setEventNumber(42)
                                        .setEventType("type")
                                        .setDataContentType(0)
                                        .setMetadataContentType(0)
                                        .setData(ByteString.copyFrom("{}".getBytes())))
                                .setCommitPosition(0)
                                .setPreparePosition(0)
                                .build()));
        Assert.assertEquals(1, seenEvents.get());
    }

    private CompletableFuture<StreamEventsSlice> wrapFuture(final StreamEventsSlice stream) {
        return CompletableFuture.completedFuture(stream);
    }
}
