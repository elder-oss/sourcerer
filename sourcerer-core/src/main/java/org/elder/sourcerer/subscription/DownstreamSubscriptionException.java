package org.elder.sourcerer.subscription;

public class DownstreamSubscriptionException extends RuntimeException {
    public DownstreamSubscriptionException(final Throwable error) {
        super(error);
    }
}
