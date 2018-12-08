package org.elder.sourcerer2.subscription;

import org.elder.sourcerer2.EventRecord;

class Update<T> {
    private final UpdateType updateType;
    private final Throwable error;
    private final EventRecord<T> event;

    private Update(
            final UpdateType updateType,
            final Throwable error,
            final EventRecord<T> event) {
        this.updateType = updateType;
        this.error = error;
        this.event = event;
    }

    public static <T> Update<T> createEvent(final EventRecord<T> event) {
        return new Update<>(
                UpdateType.EVENT,
                null,
                event);
    }

    public static <T> Update<T> createError(final Throwable error) {
        return new Update<>(
                UpdateType.ERROR,
                error,
                null);
    }

    public static <T> Update<T> createCompleted() {
        return new Update<>(
                UpdateType.COMPLETED,
                null,
                null);
    }

    public static <T> Update<T> createCaughtUp() {
        return new Update<>(
                UpdateType.CAUGHT_UP,
                null,
                null);
    }

    public UpdateType getUpdateType() {
        return updateType;
    }

    public Throwable getError() {
        return error;
    }

    public EventRecord<T> getEvent() {
        return event;
    }
}
