package org.elder.sourcerer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * The result of a successfully application of a command against the current state of an aggregate.
 */
public class CommandResult<TEvent> {
    private final String aggregateId;
    private final Integer previousVersion;
    private final Integer newVersion;
    private final ImmutableList<? extends TEvent> events;

    public CommandResult(
            final String aggregateId,
            final Integer previousVersion,
            final Integer newVersion,
            final ImmutableList<? extends TEvent> events) {
        Preconditions.checkNotNull(aggregateId);
        this.previousVersion = previousVersion;
        this.newVersion = newVersion;
        this.aggregateId = aggregateId;
        this.events = events;
    }

    public CommandResult(
            final String aggregateId,
            final Integer previousVersion,
            final Integer newVersion,
            final List<? extends TEvent> events) {
        this(aggregateId, previousVersion, newVersion, ImmutableList.copyOf(events));
    }

    /**
     * Gets the id of the aggregate that was operated on.
     */
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * Gets the previous version of the aggregate, after the events from the command were applied.
     * This may be null in the cases where the current version is unknown, e.g. a no-op operation.
     * For non atomic operations, this may be deduced from the new version and number of events
     * written, rather than by explicitly reading from the stream before appending changes.
     */
    public Integer getPreviousVersion() {
        return previousVersion;
    }

    /**
     * Gets the new version of the aggregate, after the events from the command were applied.
     * This may be null in the cases where the current version is unknown, e.g. a no-op operation.
     */
    public Integer getNewVersion() {
        return newVersion;
    }

    /**
     * Gets the new commands applied by this event.
     */
    public ImmutableList<? extends TEvent> getEvents() {
        return events;
    }
}
