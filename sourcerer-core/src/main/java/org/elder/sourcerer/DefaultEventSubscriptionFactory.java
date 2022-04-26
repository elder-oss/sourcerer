package org.elder.sourcerer;

import javax.inject.Inject;

public class DefaultEventSubscriptionFactory<T> implements EventSubscriptionFactory<T> {
    private final EventRepository<T> repository;

    @Inject
    public DefaultEventSubscriptionFactory(final EventRepository<T> repository) {
        this.repository = repository;
    }

    @Override
    public EventSubscription fromSubscriptionHandler(
            final EventSubscriptionHandler<T> handler,
            final SubscriptionWorkerConfig config) {
        return new DefaultEventSubscription<T>(repository, handler, config);
    }
}
