package org.elder.sourcerer.eventstoredb;

public class EventStoreSubscriptionStoppedException extends Throwable {
    public EventStoreSubscriptionStoppedException(final Throwable exception) {
        super(exception);
    }

    @Override
    public String toString() {
        return "EventStoreSubscriptionStoppedException{" +
                "exception=" + getCause() +
                '}';
    }
}
