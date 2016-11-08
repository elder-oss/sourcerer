package org.elder.sourcerer.esjc;

import com.github.msemys.esjc.SubscriptionDropReason;

public class EventStoreSubscriptionStoppedException extends Throwable {
    private final SubscriptionDropReason reason;
    private final Exception exception;

    public EventStoreSubscriptionStoppedException(
            final SubscriptionDropReason reason,
            final Exception exception) {
        super(exception);
        this.reason = reason;
        this.exception = exception;
    }

    public SubscriptionDropReason getReason() {
        return reason;
    }

    public Exception getException() {
        return exception;
    }

    @Override
    public String toString() {
        return "EventStoreSubscriptionStoppedException{" +
                "reason=" + reason +
                ", exception=" + exception +
                '}';
    }
}
