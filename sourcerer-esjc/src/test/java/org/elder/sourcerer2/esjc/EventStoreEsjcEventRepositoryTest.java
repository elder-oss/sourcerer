package org.elder.sourcerer2.esjc;

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
import org.elder.sourcerer2.EventSubscriptionUpdate;
import org.elder.sourcerer2.StreamId;
import org.elder.sourcerer2.StreamReadResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;
import reactor.core.subscriber.Subscribers;

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
        StreamReadResult<Event> response = repository.read(StreamId.ofString("stream"));
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
        StreamReadResult<Event> response = repository.read(StreamId.ofString("stream"));
        Assert.assertNull(response);
    }

    @Test(expected = RuntimeException.class)
    public void errorNotPropagatedWhenCancelHandlerThrows() throws IOException {
        // This is not a nice behavior, but test is to confirm root cause of issue seen live, where
        // a subscription dies and does not recover as we time out trying to stop the subscription
        // that we're currently handling an error for!
        StreamId streamId = StreamId.ofString("test-stream");

        when(reader.readValue((byte[]) any())).thenReturn(new Object());
        // Subscribe call not yet mocked, ensures we don't call subscribe until we subscribe
        // to the Flux
        Publisher<EventSubscriptionUpdate<Event>> publisher = repository.getStreamPublisher(
                streamId,
                null);

        // Set up subscription - should trigger a call to underlying subscribe
        CatchUpSubscription catchUpSubscription = mock(CatchUpSubscription.class);
        when(eventStore.subscribeToStreamFrom(
                anyString(),
                any(Integer.class),
                any(CatchUpSubscriptionSettings.class),
                any(CatchUpSubscriptionListener.class),
                any(UserCredentials.class)))
                .thenReturn(catchUpSubscription);

        // Hook up fake listener, checking that we're getting notified
        AtomicInteger seenEvents = new AtomicInteger(0);
        AtomicReference<Throwable> seenError = new AtomicReference<>(null);
        AtomicBoolean seenStop = new AtomicBoolean(false);
        publisher.subscribe(Subscribers.bounded(
                100,
                event -> seenEvents.incrementAndGet(),
                seenError::set,
                () -> seenStop.set(true)));

        ArgumentCaptor<CatchUpSubscriptionListener> listenerCaptor =
                ArgumentCaptor.forClass(CatchUpSubscriptionListener.class);

        verify(eventStore, times(1)).subscribeToStreamFrom(
                eq("pref-" + streamId.getIdentifier()),
                eq(null),
                any(CatchUpSubscriptionSettings.class),
                listenerCaptor.capture());

        CatchUpSubscriptionListener listener = listenerCaptor.getValue();
        Mockito
                .doThrow(new RuntimeException("bad stuff on close"))
                .when(catchUpSubscription).stop();
        listener.onClose(
                catchUpSubscription,
                SubscriptionDropReason.CatchUpError,
                new RuntimeException("bad things happen"));

        Assert.assertEquals(0, seenEvents.get());
        Assert.assertNull(seenError.get());
    }

    @Test
    public void errorPropagatedFromEsjc() throws IOException {
        StreamId streamId = StreamId.ofString("test-stream");

        when(reader.readValue((byte[]) any())).thenReturn(new Object());
        // Subscribe call not yet mocked, ensures we don't call subscribe until we subscribe
        // to the Flux
        Publisher<EventSubscriptionUpdate<Event>> publisher = repository.getStreamPublisher(
                streamId,
                null);

        // Set up subscription - should trigger a call to underlying subscribe
        CatchUpSubscription catchUpSubscription = mock(CatchUpSubscription.class);
        when(eventStore.subscribeToStreamFrom(
                anyString(),
                any(Integer.class),
                any(CatchUpSubscriptionSettings.class),
                any(CatchUpSubscriptionListener.class)))
                .thenReturn(catchUpSubscription);

        // Hook up fake listener, checking that we're getting notified
        AtomicInteger seenEvents = new AtomicInteger(0);
        AtomicReference<Throwable> seenError = new AtomicReference<>(null);
        AtomicBoolean seenStop = new AtomicBoolean(false);
        publisher.subscribe(Subscribers.bounded(
                100,
                event -> seenEvents.incrementAndGet(),
                seenError::set,
                () -> seenStop.set(true)));

        ArgumentCaptor<CatchUpSubscriptionListener> listenerCaptor =
                ArgumentCaptor.forClass(CatchUpSubscriptionListener.class);

        verify(eventStore, times(1)).subscribeToStreamFrom(
                eq("pref-" + streamId.getIdentifier()),
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
    public void subscriptionCallbackAppliesBackpressure() throws IOException {
        // The ESJC client applies backpressure implicitly by blocking the callback, we need to
        // ensure that the use of Reactor doesn't introduce any issues with multiple threads
        // preventing this behavior
        StreamId streamId = StreamId.ofString("test-stream");

        when(reader.readValue((byte[]) any())).thenReturn(new Object());
        // Subscribe call not yet mocked, ensures we don't call subscribe until we subscribe
        // to the Flux
        Publisher<EventSubscriptionUpdate<Event>> publisher = repository.getStreamPublisher(
                streamId,
                null);

        // Set up subscription - should trigger a call to underlying subscribe
        CatchUpSubscription catchUpSubscription = mock(CatchUpSubscription.class);
        when(eventStore.subscribeToStreamFrom(
                anyString(),
                any(Integer.class),
                any(CatchUpSubscriptionSettings.class),
                any(CatchUpSubscriptionListener.class),
                any(UserCredentials.class)))
                .thenReturn(catchUpSubscription);

        // Hook up fake listener, checking that we're getting notified
        AtomicInteger seenEvents = new AtomicInteger(0);
        publisher.subscribe(Subscribers.bounded(100, event -> {
            seenEvents.incrementAndGet();
        }));

        ArgumentCaptor<CatchUpSubscriptionListener> listenerCaptor =
                ArgumentCaptor.forClass(CatchUpSubscriptionListener.class);

        verify(eventStore, times(1)).subscribeToStreamFrom(
                eq("pref-" + streamId.getIdentifier()),
                eq(null),
                any(CatchUpSubscriptionSettings.class),
                listenerCaptor.capture());

        CatchUpSubscriptionListener listener = listenerCaptor.getValue();
        Assert.assertEquals(0, seenEvents.get());
        EventStoreClientMessages.EventRecord.Builder event =
                EventStoreClientMessages.EventRecord
                        .newBuilder()
                        .setCreatedEpoch(2342354)
                        .setEventStreamId("stream")
                        .setEventId(ByteString.copyFrom(UUIDConverter.toBytes(UUID.randomUUID())))
                        .setEventNumber(42)
                        .setEventType("type")
                        .setDataContentType(0)
                        .setMetadataContentType(0)
                        .setData(ByteString.copyFrom("{}".getBytes()));
        listener.onEvent(
                catchUpSubscription,
                new ResolvedEvent(
                        EventStoreClientMessages
                                .ResolvedEvent
                                .newBuilder()
                                .setEvent(event)
                                .setCommitPosition(0)
                                .setPreparePosition(0)
                                .build()));
        Assert.assertEquals(1, seenEvents.get());
    }

    @Test(expected = RuntimeException.class)
    public void errorNotPropagatedWhenCancelHandlerThrowsInCategoryPublisher() throws IOException {
        // This is not a nice behavior, but test is to confirm root cause of issue seen live, where
        // a subscription dies and does not recover as we time out trying to stop the subscription
        // that we're currently handling an error for!

        when(reader.readValue((byte[]) any())).thenReturn(new Object());
        // Subscribe call not yet mocked, ensures we don't call subscribe until we subscribe
        // to the Flux
        Publisher<EventSubscriptionUpdate<Event>> publisher =
                repository.getPublisher(null);

        // Set up subscription - should trigger a call to underlying subscribe
        CatchUpSubscription catchUpSubscription = mock(CatchUpSubscription.class);
        when(eventStore.subscribeToStreamFrom(
                anyString(),
                any(Integer.class),
                any(CatchUpSubscriptionSettings.class),
                any(CatchUpSubscriptionListener.class),
                any(UserCredentials.class)))
                .thenReturn(catchUpSubscription);

        // Hook up fake listener, checking that we're getting notified
        AtomicInteger seenEvents = new AtomicInteger(0);
        AtomicReference<Throwable> seenError = new AtomicReference<>(null);
        AtomicBoolean seenStop = new AtomicBoolean(false);
        publisher.subscribe(Subscribers.bounded(
                100,
                event -> seenEvents.incrementAndGet(),
                seenError::set,
                () -> seenStop.set(true)));

        ArgumentCaptor<CatchUpSubscriptionListener> listenerCaptor =
                ArgumentCaptor.forClass(CatchUpSubscriptionListener.class);

        verify(eventStore, times(1)).subscribeToStreamFrom(
                eq("$ce-pref"),
                eq(null),
                any(CatchUpSubscriptionSettings.class),
                listenerCaptor.capture());

        CatchUpSubscriptionListener listener = listenerCaptor.getValue();
        Mockito
                .doThrow(new RuntimeException("bad stuff on close"))
                .when(catchUpSubscription).stop();
        listener.onClose(
                catchUpSubscription,
                SubscriptionDropReason.CatchUpError,
                new RuntimeException("bad things happen"));

        Assert.assertEquals(0, seenEvents.get());
        Assert.assertNull(seenError.get());
    }

    @Test
    public void errorPropagatedFromEsjcInCategoryPublisher() throws IOException {
        when(reader.readValue((byte[]) any())).thenReturn(new Object());
        // Subscribe call not yet mocked, ensures we don't call subscribe until we subscribe
        // to the Flux
        Publisher<EventSubscriptionUpdate<Event>> publisher = repository.getPublisher(null);

        // Set up subscription - should trigger a call to underlying subscribe
        CatchUpSubscription catchUpSubscription = mock(CatchUpSubscription.class);
        when(eventStore.subscribeToStreamFrom(
                anyString(),
                any(Integer.class),
                any(CatchUpSubscriptionSettings.class),
                any(CatchUpSubscriptionListener.class)))
                .thenReturn(catchUpSubscription);

        // Hook up fake listener, checking that we're getting notified
        AtomicInteger seenEvents = new AtomicInteger(0);
        AtomicReference<Throwable> seenError = new AtomicReference<>(null);
        AtomicBoolean seenStop = new AtomicBoolean(false);
        publisher.subscribe(Subscribers.bounded(
                100,
                event -> seenEvents.incrementAndGet(),
                seenError::set,
                () -> seenStop.set(true)));

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
        Publisher<EventSubscriptionUpdate<Event>> publisher = repository.getPublisher(null);

        // Set up subscription - should trigger a call to underlying subscribe
        CatchUpSubscription catchUpSubscription = mock(CatchUpSubscription.class);
        when(eventStore.subscribeToStreamFrom(
                anyString(),
                any(Integer.class),
                any(CatchUpSubscriptionSettings.class),
                any(CatchUpSubscriptionListener.class),
                any(UserCredentials.class)))
                .thenReturn(catchUpSubscription);

        // Hook up fake listener, checking that we're getting notified
        AtomicInteger seenEvents = new AtomicInteger(0);
        publisher.subscribe(Subscribers.bounded(100, event -> {
            seenEvents.incrementAndGet();
        }));

        ArgumentCaptor<CatchUpSubscriptionListener> listenerCaptor =
                ArgumentCaptor.forClass(CatchUpSubscriptionListener.class);

        verify(eventStore, times(1)).subscribeToStreamFrom(
                eq("$ce-pref"),
                eq(null),
                any(CatchUpSubscriptionSettings.class),
                listenerCaptor.capture());

        CatchUpSubscriptionListener listener = listenerCaptor.getValue();
        Assert.assertEquals(0, seenEvents.get());
        EventStoreClientMessages.EventRecord.Builder event =
                EventStoreClientMessages.EventRecord
                        .newBuilder()
                        .setCreatedEpoch(2342354)
                        .setEventStreamId("stream")
                        .setEventId(ByteString.copyFrom(UUIDConverter.toBytes(UUID.randomUUID())))
                        .setEventNumber(42)
                        .setEventType("type")
                        .setDataContentType(0)
                        .setMetadataContentType(0)
                        .setData(ByteString.copyFrom("{}".getBytes()));
        listener.onEvent(
                catchUpSubscription,
                new ResolvedEvent(
                        EventStoreClientMessages
                                .ResolvedEvent
                                .newBuilder()
                                .setEvent(event)
                                .setCommitPosition(0)
                                .setPreparePosition(0)
                                .build()));
        Assert.assertEquals(1, seenEvents.get());
    }

    private CompletableFuture<StreamEventsSlice> wrapFuture(final StreamEventsSlice stream) {
        return CompletableFuture.completedFuture(stream);
    }
}
