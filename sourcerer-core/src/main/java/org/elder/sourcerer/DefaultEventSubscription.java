package org.elder.sourcerer;

import org.elder.sourcerer.subscription.EventSubscriptionManager;

public class DefaultEventSubscription<T> implements EventSubscription {
    private final EventRepository<T> repository;
    private final EventSubscriptionHandler<T> subscriptionHandler;
    private final int batchSize;
    private EventSubscriptionPositionSource positionSource;

    public DefaultEventSubscription(
            final EventRepository<T> repository,
            final EventSubscriptionHandler<T> subscriptionHandler,
            final int batchSize) {
        this.repository = repository;
        this.subscriptionHandler = subscriptionHandler;
        this.batchSize = batchSize;
    }

    @Override
    public void setPositionSource(final EventSubscriptionPositionSource positionSource) {
        this.positionSource = positionSource;
    }

    @Override
    public SubscriptionToken start() {
        EventSubscriptionManager<T> subscriptionManager = new EventSubscriptionManager<>(
                repository,
                positionSource,
                subscriptionHandler,
                batchSize);
        return subscriptionManager.start();
    }
}
