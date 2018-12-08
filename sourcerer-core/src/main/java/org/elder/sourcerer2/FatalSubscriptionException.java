package org.elder.sourcerer2;

/**
 * Thrown in a subscription to indicate the subscription has terminated with a fatal error,
 * potentially after one or more retries.
 */
public class FatalSubscriptionException extends RuntimeException {
    private final int retriesCount;

    public FatalSubscriptionException(
            final String message,
            final int retriesCount,
            final Throwable error) {
        super(message, error);
        this.retriesCount = retriesCount;
    }

    public int getRetriesCount() {
        return retriesCount;
    }
}
