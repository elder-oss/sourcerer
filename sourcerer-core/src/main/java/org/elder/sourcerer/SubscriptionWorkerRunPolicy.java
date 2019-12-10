package org.elder.sourcerer;

public interface SubscriptionWorkerRunPolicy {
    boolean shouldRun();

    void lastRunSuccessful();

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
