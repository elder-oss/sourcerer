package org.elder.sourcerer2;

/**
 * Configuration object for creating Subscription Workers.
 */
public class SubscriptionWorkerConfig {
    private final int batchSize;
    private final int initialRetryDelayMillis;
    private final int maxRetryDelayMillis;

    public SubscriptionWorkerConfig() {
        this.batchSize = 256;
        this.initialRetryDelayMillis = 100;
        this.maxRetryDelayMillis = 30_000;
    }

    public SubscriptionWorkerConfig(
            final int batchSize,
            final int initialRetryDelayMillis,
            final int maxRetryDelayMillis
    ) {
        this.batchSize = batchSize;
        this.initialRetryDelayMillis = initialRetryDelayMillis;
        this.maxRetryDelayMillis = maxRetryDelayMillis;
    }

    /**
     * @return maximum number of events in each batch.
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * @return initial delay before retrying after an error. Exponential backoff is based on this.
     */
    public int getInitialRetryDelayMillis() {
        return initialRetryDelayMillis;
    }

    /**
     * @return maximum delay between retries.
     */
    public int getMaxRetryDelayMillis() {
        return maxRetryDelayMillis;
    }

    public SubscriptionWorkerConfig withBatchSize(final int batchSize) {
        return new SubscriptionWorkerConfig(
                batchSize,
                this.initialRetryDelayMillis,
                this.maxRetryDelayMillis);
    }

    public SubscriptionWorkerConfig withInitialRetryDelayMillis(final int millis) {
        return new SubscriptionWorkerConfig(this.batchSize, millis, this.maxRetryDelayMillis);
    }

    public SubscriptionWorkerConfig withMaxRetryDelayMillis(final int millis) {
        return new SubscriptionWorkerConfig(this.batchSize, initialRetryDelayMillis, millis);
    }
}
