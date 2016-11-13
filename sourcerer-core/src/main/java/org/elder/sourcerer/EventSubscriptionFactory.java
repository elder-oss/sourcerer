package org.elder.sourcerer;

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
     */
    default EventSubscription fromSubscriptionHandler(final EventSubscriptionHandler<T> handler) {
        return fromSubscriptionHandler(
                handler,
                256);
    }

    /**
     * Creates a new subscription from a subscription handler, receiving all events from the event
     * streams that the subscription factory represents. Note that the subscription when returned is
     * not yet active and must be started by calling start() to receive events.
     *
     * @param handler   The handler called to process batches of events.
     * @param batchSize The maximum number of items to process in each append.
     */
    EventSubscription fromSubscriptionHandler(
            EventSubscriptionHandler<T> handler,
            int batchSize);
}
