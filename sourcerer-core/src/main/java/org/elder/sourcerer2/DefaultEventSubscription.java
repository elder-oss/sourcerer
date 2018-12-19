package org.elder.sourcerer2;

import org.elder.sourcerer2.subscription.EventSubscriptionManager;

public class DefaultEventSubscription<T> implements EventSubscription {
    private final EventRepository<T> repository;
    private final Integer shard;
    private final EventSubscriptionHandler<T> subscriptionHandler;
    private final SubscriptionWorkerConfig config;
    private EventSubscriptionPositionSource positionSource;

    public DefaultEventSubscription(
            final EventRepository<T> repository,
            final Integer shard,
            final EventSubscriptionHandler<T> subscriptionHandler,
            final SubscriptionWorkerConfig config
    ) {
        this.repository = repository;
        this.shard = shard;
        this.subscriptionHandler = subscriptionHandler;
        this.config = config;
    }

    @Override
    public void setPositionSource(final EventSubscriptionPositionSource positionSource) {
        this.positionSource = positionSource;
    }

    @Override
    public SubscriptionToken start() {
        EventSubscriptionManager<T> subscriptionManager = new EventSubscriptionManager<>(
                repository,
                shard,
                positionSource,
                subscriptionHandler,
                config);
        return subscriptionManager.start();
    }
}
