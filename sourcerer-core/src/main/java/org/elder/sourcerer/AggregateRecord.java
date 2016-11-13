package org.elder.sourcerer;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An AggregateRecord is an immutable representation of a snapshot in time of an aggregate as
 * constructed from events, along with additional metadata about the aggregate such as the current
 * version.
 * <p>
 * While an aggregate record is constructed from a sequence of events, it does not necessarily
 * contain all of the information in those events, and may be constructed from materialized
 * snapshots to improve performance.
 * <p>
 * Aggregate records should be used only by the command (write) side of an event sourced CQRS
 * implementation.
 *
 * @param <T> the type of aggregate that this class is describing
 */
public class AggregateRecord<T> {
    private final String id;
    private final T aggregate;
    private final int version;

    public AggregateRecord(
            @NotNull final String id,
            @Nullable final T aggregate,
            final int version) {
        Preconditions.checkNotNull(id);
        this.id = id;
        this.aggregate = aggregate;
        this.version = version;
    }

    /**
     * Gets the id of the wrapped aggregate.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the current state of the aggregate. May be null for non existing or deleted aggregates.
     */
    public T getAggregate() {
        return aggregate;
    }

    /**
     * Gets the current version of the aggregate. The version can be set as the expected version
     * when writing new events to the aggregate to ensure that no modifications have taken place in
     * between read and write - either to ensure atomic updates within a process, or as part of an
     * optimistic concurrency scheme across the larger system.
     */
    public int getVersion() {
        return version;
    }
}
