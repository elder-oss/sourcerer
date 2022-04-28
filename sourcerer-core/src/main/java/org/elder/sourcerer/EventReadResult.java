package org.elder.sourcerer;

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

    /**
     * Get the version used as the "from" version when the stream was read (matching the version of the
     * first event read, if any were read).
     */
    public int getFromVersion() {
        return fromVersion;
    }

    /**
     * The last version currently known to be available for a given stream. If isEndOfStream is set to true,
     * this is the last event version currently available in the underlying data store. If isEndOfStream is
     * false, the current last version in the data store may be more recent than this.
     */
    public int getLastVersion() {
        return lastVersion;
    }

    /**
     * The version to use for reading the next page / batch.
     */
    public int getNextVersion() {
        return nextVersion;
    }

    public boolean isEndOfStream() {
        return isEndOfStream;
    }
}
