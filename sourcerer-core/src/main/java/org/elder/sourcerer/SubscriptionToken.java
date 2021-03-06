package org.elder.sourcerer;

/**
 * Handle connected to the lifetime of a subscription, allowing it to be gracefully stopped.
 */
public interface SubscriptionToken {
    /**
     * Stops the subscription. This method is safe to call from multiple threads, and multiple
     * times. Note that, while calling this method will result in a subscription stopping, a
     * subscription handler may still receive events for some time after it has completed.
     */
    void stop();
}
