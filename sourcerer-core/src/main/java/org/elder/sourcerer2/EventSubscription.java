package org.elder.sourcerer2;

public interface EventSubscription {
    /**
     * Sets the position source to use for this subscription. A subscription without a position
     * source will always consume all events, including historical from beginning of time.
     *
     * @param positionSource The position source used to query for the current position of the
     *                       logical subscription.
     */
    void setPositionSource(EventSubscriptionPositionSource positionSource);

    /**
     * Starts the subscription. Once called, the configured subscription handler will start
     * receiving events based on the current configuration of the subscription - modifications to
     * the subscription object will have no impact on current subscriptions once started.
     * <p>
     * To stop the subscription, call .close() on the returned Closeable object. Note that, due to
     * multiple threads being used, the subscription handler may still receive events for some time
     * after close() has been called.
     *
     * @return A closable token use to stop the subscription from processing.
     */
    SubscriptionToken start();
}
