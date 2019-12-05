package org.elder.sourcerer2.subscription;

import com.google.common.collect.ImmutableMap;
import org.elder.sourcerer2.EventId;
import org.elder.sourcerer2.EventRecord;
import org.elder.sourcerer2.EventRepository;
import org.elder.sourcerer2.EventSubscriptionPositionSource;
import org.elder.sourcerer2.EventSubscriptionUpdate;
import org.elder.sourcerer2.RepositoryVersion;
import org.elder.sourcerer2.StreamId;
import org.elder.sourcerer2.StreamVersion;
import org.elder.sourcerer2.SubscriptionToken;
import org.elder.sourcerer2.SubscriptionWorkerConfig;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EventSubscriptionManagerTest {
    // TODO: Rewrite and re-enable
    /*
    private static class SlowSubscriptionHandler<T> extends AbstractSubscriptionHandler<T> {
        private static final Logger logger =
                LoggerFactory.getLogger(AbstractSubscriptionHandler.class);
        private T lastSeenValue;
        private int totalEvents = 0;

        @Override
        public void processEvents(final List<EventRecord<T>> list) {
            logger.info("Seeing {} events", list.size());
            lastSeenValue = list.get(list.size() - 1).getEvent();
            totalEvents += list.size();
            sleep(100);
        }

        @Override
        public boolean handleError(final Throwable error, final int retryCount) {
            logger.info("Seeing error and {} retries", retryCount);
            return true;
        }

        public T getLastSeenValue() {
            return lastSeenValue;
        }

        public int getTotalEvents() {
            return totalEvents;
        }
    }

    private static class ErroringSubscriptionHandler<T> extends AbstractSubscriptionHandler<T> {
        private static final Logger logger =
                LoggerFactory.getLogger(AbstractSubscriptionHandler.class);
        private final int failAt;
        private int position = 0;

        public ErroringSubscriptionHandler(final int failAt) {
            this.failAt = failAt;
        }

        @Override
        public void processEvents(final List<EventRecord<T>> list) {
            logger.info("Seeing {} events", list.size());
            if (position >= failAt) {
                position = 0;
                throw new IllegalStateException("Bad stuff!!");
            }
            position += list.size();
            sleep(10);
        }

        @Override
        public boolean handleError(final Throwable error, final int retryCount) {
            logger.info("Seeing error and {} retries", retryCount);
            return true;
        }
    }

    private static final Logger
            logger
            = LoggerFactory.getLogger(EventSubscriptionManagerTest.class);
    private String lastProducedValue;

    @Test
    @Ignore // Manual only
    public void testBackpressureWorking() {
        EventRepository<String> repository = mock(EventRepository.class);
        EventSubscriptionPositionSource positionSource =
                mock(EventSubscriptionPositionSource.class);

        WorkQueueProcessor<EventSubscriptionUpdate<String>> processor = WorkQueueProcessor.create();
        Flux<EventSubscriptionUpdate<String>> eventSource = Flux
                .fromStream(IntStream
                        .range(0, 1000000)
                        .mapToObj(this::wrapIntAsEvent)
                        .map(EventSubscriptionUpdate.Companion::ofEvent))
                .doOnNext(e -> {
                    lastProducedValue = e.getEvent().getEvent();
                });
        eventSource.subscribe(processor);

        when(repository.subscribe(any(), any())).thenReturn(processor);
        when(positionSource.getSubscriptionPosition()).thenReturn(null);
        SlowSubscriptionHandler<String> subscriptionHandler = new SlowSubscriptionHandler<>();

        EventSubscriptionManager subscriptionManager = new EventSubscriptionManager<>(
                repository,
                null,
                positionSource,
                subscriptionHandler,
                new SubscriptionWorkerConfig().withBatchSize(64));

        SubscriptionToken token = subscriptionManager.start();
        sleep(5000);
        token.stop();

        logger.info(
                "Last produced value was {}, last seen {}, total events seen: {}",
                lastProducedValue,
                subscriptionHandler.getLastSeenValue(),
                subscriptionHandler.getTotalEvents());
    }

    @Test
    @Ignore // Manual only
    public void testRetriesOnHandlerError() {
        EventRepository<String> repository = mock(EventRepository.class);
        EventSubscriptionPositionSource positionSource =
                mock(EventSubscriptionPositionSource.class);

        when(repository.subscribe(any(), any())).then(position -> {
            WorkQueueProcessor<EventSubscriptionUpdate<String>> processor =
                    WorkQueueProcessor.create();
            Flux<EventSubscriptionUpdate<String>> eventSource = Flux
                    .fromStream(IntStream
                            .range(0, 1000000)
                            .mapToObj(this::wrapIntAsEvent)
                            .map(EventSubscriptionUpdate.Companion::ofEvent))
                    .doOnNext(e -> {
                        lastProducedValue = e.getEvent().getEvent();
                    });
            eventSource.subscribe(processor);
            return processor;
        });

        when(positionSource.getSubscriptionPosition()).thenReturn(null);
        ErroringSubscriptionHandler<String> subscriptionHandler =
                new ErroringSubscriptionHandler<>(300);

        EventSubscriptionManager subscriptionManager = new EventSubscriptionManager<>(
                repository,
                null,
                positionSource,
                subscriptionHandler,
                new SubscriptionWorkerConfig().withBatchSize(64));

        SubscriptionToken token = subscriptionManager.start();
        sleep(100000);
        token.stop();
    }

    @Test
    @Ignore // Manual only
    public void testRetriesOnHandlerErrorWithBackoff() {
        EventRepository<String> repository = mock(EventRepository.class);
        EventSubscriptionPositionSource positionSource =
                mock(EventSubscriptionPositionSource.class);

        when(repository.subscribe(any(), any())).then(position -> {
            WorkQueueProcessor<EventRecord<String>> processor = WorkQueueProcessor.create();
            Flux<EventRecord<String>> eventSource = Flux
                    .fromStream(IntStream.range(0, 1000000).mapToObj(this::wrapIntAsEvent))
                    .doOnNext(e -> {
                        lastProducedValue = e.getEvent();
                    });
            eventSource.subscribe(processor);
            return processor;
        });

        when(positionSource.getSubscriptionPosition()).thenReturn(null);
        ErroringSubscriptionHandler<String> subscriptionHandler =
                new ErroringSubscriptionHandler<>(0);

        EventSubscriptionManager subscriptionManager = new EventSubscriptionManager<>(
                repository,
                null,
                positionSource,
                subscriptionHandler,
                new SubscriptionWorkerConfig().withBatchSize(64));

        SubscriptionToken token = subscriptionManager.start();
        sleep(100000);
        token.stop();
    }

    @Test
    @Ignore // Manual only
    public void testRetriesOnStreamError() {
        EventRepository<String> repository = mock(EventRepository.class);
        EventSubscriptionPositionSource positionSource =
                mock(EventSubscriptionPositionSource.class);

        when(repository.subscribe(any(), any())).then(position -> {
            WorkQueueProcessor<EventRecord<String>> processor = WorkQueueProcessor.create();
            Flux<EventRecord<String>> eventSource = Flux
                    .fromStream(IntStream.range(0, 1000000).mapToObj(this::wrapIntAsEvent))
                    .doOnNext(e -> {
                        lastProducedValue = e.getEvent();
                    });
            eventSource.subscribe(processor);
            return processor.take(100).thenMany(Flux.error(new RuntimeException("fail!")));
        });

        when(positionSource.getSubscriptionPosition()).thenReturn(null);
        SlowSubscriptionHandler<String> subscriptionHandler =
                new SlowSubscriptionHandler<>();

        EventSubscriptionManager subscriptionManager = new EventSubscriptionManager<>(
                repository,
                null,
                positionSource,
                subscriptionHandler,
                new SubscriptionWorkerConfig().withBatchSize(64));

        SubscriptionToken token = subscriptionManager.start();
        sleep(100000);
        token.stop();
    }

    private EventRecord<String> wrapIntAsEvent(final int sequenceNum) {
        return new EventRecord<>(
                EventId.newUniqueId(),
                StreamId.ofString("streamId"),
                StreamVersion.ofInt(sequenceNum),
                RepositoryVersion.ofInt(1000 + sequenceNum),
                "intEvent",
                Instant.now(),
                ImmutableMap.of(),
                Integer.toString(sequenceNum));
    }

    private static void sleep(final int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException expected) {
        }
    }
    */
}
