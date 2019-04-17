package org.elder.sourcerer.utils;

public class RetryPolicy {
    private final long initialDelayMillis;
    private final long backoffFactorMillis;
    private final int maxAttempts;

    public RetryPolicy(
            final long initialDelayMillis,
            final long backoffFactorMillis,
            final int maxAttempts
    ) {
        this.initialDelayMillis = initialDelayMillis;
        this.backoffFactorMillis = backoffFactorMillis;
        this.maxAttempts = maxAttempts;
    }

    public static RetryPolicy noRetries() {
        return new RetryPolicy(0, 0, 0);
    }

    public long getInitialDelayMillis() {
        return initialDelayMillis;
    }

    public long getBackoffFactorMillis() {
        return backoffFactorMillis;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    @Override
    public String toString() {
        return "RetryPolicy{" +
                "initialDelayMillis=" + initialDelayMillis +
                ", backoffFactorMillis=" + backoffFactorMillis +
                ", maxAttempts=" + maxAttempts +
                '}';
    }
}
