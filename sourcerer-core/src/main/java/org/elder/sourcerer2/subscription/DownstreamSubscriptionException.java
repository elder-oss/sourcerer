package org.elder.sourcerer2.subscription;

public class DownstreamSubscriptionException extends RuntimeException {
    public DownstreamSubscriptionException(final Throwable error) {
        super(error);
    }
}
