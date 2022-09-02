package org.elder.sourcerer.eventstoredb;

/**
 * Thrown by the emitter listener when the subscription is cancelled.
 */
public class SubscriptionCancelledException extends RuntimeException {
    public SubscriptionCancelledException(final String name) {
        super("Subscription " + name + " was cancelled");
    }
}
