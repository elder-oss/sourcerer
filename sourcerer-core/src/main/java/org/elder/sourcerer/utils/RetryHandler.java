package org.elder.sourcerer.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Atomic update retry handler. This class keeps track of the number of failed attempts have been
 * made, and for how long to back off before trying again.
 * <p>
 * The backoff is configured, but there is a random variance of +-50% to avoid multiple clients
 * continuously colliding.
 */
public class RetryHandler {
    private static final Logger logger = LoggerFactory.getLogger(RetryHandler.class);
    private static final long MAX_BACK_OFF_MILLIS = 5_000;
    private final RetryPolicy policy;
    private int nrFailures = 0;

    public RetryHandler(final RetryPolicy policy) {
        this.policy = policy;
    }

    public void failed() {
        nrFailures++;
    }

    public int getNrFailures() {
        return nrFailures;
    }

    public boolean isThresholdReached() {
        return nrFailures >= policy.getMaxAttempts();
    }

    public void backOff() {
        try {
            long backoffFactor = policy.getBackoffFactorMillis() << (nrFailures - 1);
            long totalBackoff = policy.getInitialDelayMillis() + backoffFactor;
            long sleepTime = Math.min(totalBackoff, MAX_BACK_OFF_MILLIS);
            logger.debug("Backing off for {}ms", sleepTime);
            Thread.sleep(sleepTime);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
