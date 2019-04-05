package org.elder.sourcerer.utils;

public class RetryPolicy {
    private final int maxAttempts;
    private final int backoffFactorMillis;

    public RetryPolicy(final int maxAttempts, final int backoffFactorMillis) {
        this.maxAttempts = maxAttempts;
        this.backoffFactorMillis = backoffFactorMillis;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getBackoffFactorMillis() {
        return backoffFactorMillis;
    }
}
