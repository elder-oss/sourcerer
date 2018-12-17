package org.elder.sourcerer2.subscription;

import org.elder.sourcerer2.EventRepository;
import org.elder.sourcerer2.EventSubscriptionHandler;
import org.elder.sourcerer2.EventSubscriptionPositionSource;
import org.elder.sourcerer2.SubscriptionToken;
import org.elder.sourcerer2.SubscriptionWorkerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom subscriber to work around the fact that buffer does not appear to respect back pressure,
 * in RxJava or Project Reactor! This has the added benefit of not requiring time windows, but
 * rather process updates in batch automatically if the subscriber is not fast enough to keep up
 * with them sent one by one - i.e. normally batch in replay model, and one by one delivery when
 * live - but without added latency in the live case.
 * <p>
 * This implementation uses a dedicated worker thread per subscription, which is expensive but easy
 * to manage.
 *
 * @param <T> The type of events the subscription handles.
 */
public class EventSubscriptionManager<T> {
    private static final Logger logger = LoggerFactory.getLogger(EventSubscriptionManager.class);

    private final SubscriptionWorker subscriptionWorker;
    private final Thread workerThread;

    public EventSubscriptionManager(
            final EventRepository<T> repository,
            final Integer shard,
            final EventSubscriptionPositionSource positionSource,
            final EventSubscriptionHandler<T> subscriptionHandler,
            final SubscriptionWorkerConfig config) {
        this.subscriptionWorker = new SubscriptionWorker<>(
                repository,
                shard,
                positionSource,
                subscriptionHandler,
                config);
        this.workerThread = new Thread(subscriptionWorker, "event-subscription-worker");
        this.workerThread.setUncaughtExceptionHandler(uncaughtExceptionHandler());
    }

    public SubscriptionToken start() {
        workerThread.start();
        return subscriptionWorker;
    }

    private Thread.UncaughtExceptionHandler uncaughtExceptionHandler() {
        return (thread, throwable) -> {
            logger.error("Uncaught exception in worker - stopped: ", throwable);
        };
    }
}
