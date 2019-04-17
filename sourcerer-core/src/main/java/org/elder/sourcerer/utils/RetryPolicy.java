package org.elder.sourcerer.utils;

public class RetryPolicy {
    private static final int DEFAULT_MAX_BACKOFF_MILLIS1 = 5_000;

    private final long initialDelayMillis;
    private final long backoffFactorMillis;
    private final int maxAttempts;
    private final long maxBackoffMillis;

    public RetryPolicy(
            final long initialDelayMillis,
            final long backoffFactorMillis,
            final int maxAttempts
    ) {
        this.initialDelayMillis = initialDelayMillis;
        this.backoffFactorMillis = backoffFactorMillis;
        this.maxAttempts = maxAttempts;
        this.maxBackoffMillis = DEFAULT_MAX_BACKOFF_MILLIS1;
    }

    public RetryPolicy(
            final long initialDelayMillis,
            final long backoffFactorMillis,
            final int maxAttempts,
            final long maxBackoffMillis
    ) {
        this.initialDelayMillis = initialDelayMillis;
        this.backoffFactorMillis = backoffFactorMillis;
        this.maxAttempts = maxAttempts;
        this.maxBackoffMillis = maxBackoffMillis;
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

    public long getMaxBackoffMillis() {
        return maxBackoffMillis;
    }

    @Override
    public String toString() {
        return "RetryPolicy{" +
                "initialDelayMillis=" + initialDelayMillis +
                ", backoffFactorMillis=" + backoffFactorMillis +
                ", maxAttempts=" + maxAttempts +
                ", maxBackoffMillis=" + maxBackoffMillis +
                '}';
    }
}
