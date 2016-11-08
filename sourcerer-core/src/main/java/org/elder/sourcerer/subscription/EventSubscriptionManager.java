package org.elder.sourcerer.subscription;

import org.elder.sourcerer.EventRepository;
import org.elder.sourcerer.EventSubscriptionHandler;
import org.elder.sourcerer.EventSubscriptionPositionSource;
import org.elder.sourcerer.SubscriptionToken;

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
    private final SubscriptionWorker subscriptionWorker;
    private final Thread workerThread;

    public EventSubscriptionManager(
            final EventRepository<T> repository,
            final EventSubscriptionPositionSource positionSource,
            final EventSubscriptionHandler<T> subscriptionHandler,
            final int batchSize) {
        this.subscriptionWorker = new SubscriptionWorker<>(
                repository,
                positionSource,
                subscriptionHandler,
                batchSize);
        this.workerThread = new Thread(subscriptionWorker, "event-subscription-worker");
    }

    public SubscriptionToken start() {
        workerThread.start();
        return subscriptionWorker;
    }
}
