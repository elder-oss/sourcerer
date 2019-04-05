package org.elder.sourcerer.utils;

import java.util.Random;

public class RetryHandlerFactory {
    private final Random rnd = new Random();
    private final RetryPolicy retryPolicy;

    public RetryHandlerFactory(final RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    public static RetryHandlerFactory noRetries() {
        return new RetryHandlerFactory(new RetryPolicy(0, 0));
    }

    public RetryHandler newRetryHandler() {
        return new RetryHandler(retryPolicy, rnd);
    }
}
