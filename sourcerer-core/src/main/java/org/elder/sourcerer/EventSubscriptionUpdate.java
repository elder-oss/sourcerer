package org.elder.sourcerer;

public class EventSubscriptionUpdate<T> {
    public enum UpdateType {
        EVENT,
        CAUGHT_UP,
    }

    private UpdateType updateType;
    private EventRecord<T> event;

    public EventSubscriptionUpdate(final UpdateType updateType, final EventRecord<T> event) {
        this.updateType = updateType;
        this.event = event;
    }

    public UpdateType getUpdateType() {
        return updateType;
    }

    public EventRecord<T> getEvent() {
        return event;
    }

    public static <T> EventSubscriptionUpdate<T> ofEvent(final EventRecord<T> event) {
        return new EventSubscriptionUpdate<>(UpdateType.EVENT, event);
    }
}
