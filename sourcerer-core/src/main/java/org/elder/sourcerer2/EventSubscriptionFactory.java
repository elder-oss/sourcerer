package org.elder.sourcerer2;

/**
 * The subscription factory is a higher level abstraction normally expressed in terms of an {@link
 * EventRepository} that manages the lifetime of subscriptions to event streams.
 */
public interface EventSubscriptionFactory<T> {
    /**
     * Creates a new subscription from a subscription handler, receiving all events from the event
     * streams that the subscription factory represents. Note that the subscription when returned is
     * not yet active and must be started by calling start() to receive events.
     *
     * @param handler The handler called to process batches of events.
     * @param config  Config to use when instantiating subscription workers.
     * @param shard   The shard to subscribe to. If null, all events in the underlying repository
     *                will be processed, if set, then only those in that specific sub slice.
     */
    EventSubscription fromSubscriptionHandler(
            EventSubscriptionHandler<T> handler,
            SubscriptionWorkerConfig config,
            Integer shard);

    default EventSubscription fromSubscriptionHandler(EventSubscriptionHandler<T> handler) {
        return fromSubscriptionHandler(handler, new SubscriptionWorkerConfig(), null);
    }
}
