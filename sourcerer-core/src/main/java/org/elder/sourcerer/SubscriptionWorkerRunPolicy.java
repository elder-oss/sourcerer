package org.elder.sourcerer;

public interface SubscriptionWorkerRunPolicy {
    boolean shouldRun();

    public static SubscriptionWorkerRunPolicy ALWAYS = () -> true;
}
