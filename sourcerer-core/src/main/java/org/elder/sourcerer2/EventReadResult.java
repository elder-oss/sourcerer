package org.elder.sourcerer2;

import com.google.common.collect.ImmutableList;

public class EventReadResult<T> {
    private final ImmutableList<EventRecord<T>> events;
    private final int fromVersion;
    private final int lastVersion;
    private final int nextVersion;
    private final boolean isEndOfStream;

    public EventReadResult(
            final ImmutableList<EventRecord<T>> events,
            final int fromVersion,
            final int lastVersion,
            final int nextVersion,
            final boolean isEndOfStream) {
        this.events = events;
        this.fromVersion = fromVersion;
        this.lastVersion = lastVersion;
        this.nextVersion = nextVersion;
        this.isEndOfStream = isEndOfStream;
    }

    public ImmutableList<EventRecord<T>> getEvents() {
        return events;
    }

    public int getFromVersion() {
        return fromVersion;
    }

    public int getLastVersion() {
        return lastVersion;
    }

    public int getNextVersion() {
        return nextVersion;
    }

    public boolean isEndOfStream() {
        return isEndOfStream;
    }
}
