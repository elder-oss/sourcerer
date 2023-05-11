package org.elder.sourcerer;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The AggregateRepository providers a higher level abstraction relative to EventRepository, and
 * would typically be implemented in terms of one. While EventRepository only understands events,
 * the AggregateRepository is also aware of how aggregates are constructed from events, and allows
 * for functions operating on this level such as commands.
 */
public interface AggregateRepository<TState, TEvent> {
    /**
     * Loads an an aggregate given an aggregate id. The construction of an aggregate is
     * implementation specific, but would be semantically equivalent to applying a projection to
     * each recorded event for the aggregate.
     *
     * @param aggregateId The id of the aggregate to load.
     * @return A snapshot in time of the aggregate along with information such as its current
     * version. This method should never return null, but rather an ImmutableAggregate with a
     * version of Aggregate.VERSION_NOT_CREATED (-1) if the aggregate is nonexistent or deleted.
     */
    ImmutableAggregate<TState, TEvent> load(String aggregateId);

    /**
     * Loads an aggregate given an aggregate id and the initial snapshot.
     * The construction of an aggregate is implementation specific, but would be semantically
     * equivalent to applying a projection to each recorded event for the aggregate from
     * the initial position and state.
     *
     * @param aggregateId The id of the aggregate to load.
     * @param snapshot The snapshot to start from.
     *
     * @return A snapshot in time of the aggregate along with information such as its current
     * version. This method should never return null, but rather an ImmutableAggregate with a
     * version of Aggregate.VERSION_NOT_CREATED (-1) if the aggregate is nonexistent or deleted.
     */
    ImmutableAggregate<TState, TEvent> loadFromSnapshot(
            String aggregateId,
            Snapshot<TState> snapshot
    );

    /**
     * Persists a given existing or new aggregate with a list of events taken from an aggregate
     * state. The id of the aggregate and expected version (if used) will be taken from the details
     * of the aggregate state, and all pending events on the state will be persisted.
     *
     * @param aggregate The aggregate to apply updates for.
     * @param atomic    If true, the original version of the aggregate will be used as the expected
     *                  version, failing the operation if other changes have taken place in
     *                  between.
     * @param metadata  Metadata in the form of string key/value pairs used to annotate each event
     *                  as persisted. Metadata will not be used to reconstruct the aggregate from
     *                  events, but can be used to append diagnostic information to the operation,
     *                  such as who performed the action that triggered it, or which system that
     *                  submitted the events.
     */
    ImmutableAggregate<TState, TEvent> save(
            @NotNull Aggregate<TState, TEvent> aggregate,
            boolean atomic,
            Map<String, String> metadata);

    /**
     * Persists a given existing or new aggregate with a list of events taken from an aggregate
     * state. The id of the aggregate and expected version (if used) will be taken from the details
     * of the aggregate state, and all pending events on the state will be persisted.
     *
     * @param aggregate The aggregate to apply updates for.
     * @param atomic    If true, the original version of the aggregate will be used as the expected
     *                  version, failing the operation if other changes have taken place in
     *                  between.
     */
    default ImmutableAggregate<TState, TEvent> save(
            @NotNull Aggregate<TState, TEvent> aggregate,
            boolean atomic) {
        return save(aggregate, atomic, null);
    }

    /**
     * Persists a given existing or new aggregate with a list of events taken from an aggregate
     * state. The id of the aggregate and expected version (if used) will be taken from the details
     * of the aggregate state, and all pending events on the state will be persisted.
     *
     * @param aggregate The aggregate to apply updates for.
     */
    default ImmutableAggregate<TState, TEvent> save(
            @NotNull Aggregate<TState, TEvent> aggregate) {
        return save(aggregate, true, null);
    }

    /**
     * Appends events, provided explicitly, to a given existing or new aggregate, optionally
     * requiring the current version to match the specified requirements.
     *
     * @param aggregateId     The id of the aggregate to append events do.
     * @param events          The list of events to append.
     * @param expectedVersion The expected version of the aggregate at the time the new events are
     *                        appended to its underlying stream of events. If the version at the
     *                        time the append is attempted differs from the one provided, an
     *                        UnexpectedVersionException will be thrown.
     * @param metadata        Metadata in the form of string key/value pairs used to annotate each
     *                        event as persisted. Metadata will not be used to reconstruct the
     *                        aggregate from events, but can be used to append diagnostic
     *                        information to the operation, such as who performed the action that
     *                        triggered it, or which system that submitted the events.
     */
    int append(
            String aggregateId,
            Iterable<? extends TEvent> events,
            ExpectedVersion expectedVersion,
            Map<String, String> metadata);

    /**
     * Appends events, provided explicitly, to a given existing or new aggregate, optionally
     * requiring the current version to match the specified requirements.
     *
     * @param aggregateId     The id of the aggregate to append events do.
     * @param events          The list of events to append.
     * @param expectedVersion The expected version of the aggregate at the time the new events are
     *                        appended to its underlying stream of events. If the version at the
     *                        time the append is attempted differs from the one provided, an
     *                        UnexpectedVersionException will be thrown.
     */
    default int append(
            final String aggregateId,
            final Iterable<? extends TEvent> events,
            final ExpectedVersion expectedVersion) {
        return append(aggregateId, events, expectedVersion, null);
    }

    /**
     * Appends an event, provided explicitly, to a given existing or new aggregate, optionally
     * requiring the current version to match the specified requirements.
     *
     * @param aggregateId     The id of the aggregate to append events do.
     * @param event           The event to append.
     * @param expectedVersion The expected version of the aggregate at the time the new events are
     *                        appended to its underlying stream of events. If the version at the
     *                        time the append is attempted differs from the one provided, an
     *                        UnexpectedVersionException will be thrown.
     * @param metadata        Metadata in the form of string key/value pairs used to annotate each
     *                        event as persisted. Metadata will not be used to reconstruct the
     *                        aggregate from events, but can be used to append diagnostic
     *                        information to the operation, such as who performed the action that
     *                        triggered it, or which system that submitted the events.
     */
    default int append(
            final String aggregateId,
            final TEvent event,
            final ExpectedVersion expectedVersion,
            final Map<String, String> metadata) {
        return append(aggregateId, ImmutableList.of(event), expectedVersion, metadata);
    }

    /**
     * Appends an event, provided explicitly, to a given existing or new aggregate, optionally
     * requiring the current version to match the specified requirements.
     *
     * @param aggregateId     The id of the aggregate to append events do.
     * @param event           The event to append.
     * @param expectedVersion The expected version of the aggregate at the time the new events are
     *                        appended to its underlying stream of events. If the version at the
     *                        time the append is attempted differs from the one provided, an
     *                        UnexpectedVersionException will be thrown.
     */
    default int append(
            final String aggregateId, final TEvent event, final ExpectedVersion expectedVersion) {
        return append(aggregateId, ImmutableList.of(event), expectedVersion, null);
    }
}
