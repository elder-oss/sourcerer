package org.elder.sourcerer.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Atomic update retry handler. This class keeps track of the number of failed attempts have
 * been made, and for how long to back off before trying again.
 *
 * The backoff is configured, but there is a random variance of +-50% to avoid multiple
 * clients continuously colliding.
 */
public class RetryHandler {
    private static final Logger logger = LoggerFactory.getLogger(RetryHandler.class);
    private static final Random rnd = new Random();
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
            int randomVarianceMillis =
                    rnd.nextInt(policy.getBackoffFactorMillis()) +
                    policy.getBackoffFactorMillis() / 2;
            long sleepTime = randomVarianceMillis << (nrFailures - 1);
            logger.debug("Backing off for {}ms", sleepTime);
            Thread.sleep(sleepTime);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
