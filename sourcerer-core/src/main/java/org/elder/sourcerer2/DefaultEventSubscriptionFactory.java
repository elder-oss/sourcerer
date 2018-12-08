package org.elder.sourcerer2;

public class DefaultEventSubscriptionFactory<T> implements EventSubscriptionFactory<T> {
    private final EventRepository<T> repository;

    public DefaultEventSubscriptionFactory(final EventRepository<T> repository) {
        this.repository = repository;
    }

    @Override
    public EventSubscription fromSubscriptionHandler(
            final EventSubscriptionHandler<T> handler,
            final SubscriptionWorkerConfig config) {
        return new DefaultEventSubscription(repository, handler, config);
    }
}
