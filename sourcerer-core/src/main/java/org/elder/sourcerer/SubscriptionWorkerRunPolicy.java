package org.elder.sourcerer;

public interface SubscriptionWorkerRunPolicy {
    /**
     * Checks whether subscription worker should run.
     *
     * @return true if subscription worker should start consuming events.
     */
    boolean shouldRun();

    /**
     * Called when consumer has processed a batch without throwing an exception.
     */
    void lastRunSuccessful();

    /**
     * Called when consumer has thrown an exception while processing events.
     */
    void lastRunFailed(Exception ex);

    SubscriptionWorkerRunPolicy ALWAYS = new AlwaysRunSubscriptionWorkerRunPolicy();
}

class AlwaysRunSubscriptionWorkerRunPolicy implements SubscriptionWorkerRunPolicy {
    @Override
    public boolean shouldRun() {
        return true;
    }

    @Override
    public void lastRunSuccessful() {
    }

    @Override
    public void lastRunFailed(final Exception ex) {
    }
}
