package org.elder.sourcerer2;

public interface EventSubscription {
    /**
     * Sets the position source to use for this subscription.
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
     * The subscription will automatically retry on errors and may implement a backoff scheme to
     * do so without busy looping. Attempts will be made to keep it live until the returned token
     * is explicitly closed.
     * <p>
     * To stop the subscription, call .close() on the returned Closeable object. Note that, due to
     * multiple threads being used, the subscription handler may still receive events for some time
     * after close() has been called.
     *
     * @return A closable token use to stop the subscription from processing.
     */
    SubscriptionToken start();
}
