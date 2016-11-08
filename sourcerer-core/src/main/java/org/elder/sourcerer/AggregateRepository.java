package org.elder.sourcerer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

/**
 * The AggregateRepository providers a higher level abstraction relative to EventRepository, and
 * would typically be implemented in terms of one. While EventRepository only understands events,
 * the AggregateRepository is also aware of how aggregates are constructed from events, and allows
 * for functions operating on this level such as commands.
 */
public interface AggregateRepository<TAggregate, TEvent> {
    /**
     * Reads an an aggregate given an aggregate id. The construction of an aggregate is
     * implementation specific, but would be semantically equivalent to applying a projection to
     * each recorded event for the aggregate.
     *
     * @param aggregateId The id of the aggregate to read.
     * @return A snapshot in time of the aggregate along with information such as its current
     * version. This method should never return null, but rather an AggregateRecord with a null
     * aggregate if the aggregate is nonexistent or deleted.
     */
    AggregateRecord<TAggregate> read(String aggregateId);

    /**
     * Updates a given existing or new aggregate with a list of events.
     *
     * @param aggregateId     The id of the aggregate to update.
     * @param events          The list of events to update the aggregate with.
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
    int update(
            String aggregateId,
            List<? extends TEvent> events,
            ExpectedVersion expectedVersion,
            Map<String, String> metadata);

    /**
     * Updates a given existing or new aggregate with a list of events.
     *
     * @param aggregateId     The id of the aggregate to update.
     * @param events          The list of events to update the aggregate with.
     * @param expectedVersion The expected version of the aggregate at the time the new events are
     *                        appended to its underlying stream of events. If the version at the
     *                        time the append is attempted differs from the one provided, an
     *                        UnexpectedVersionException will be thrown.
     */
    default int update(
            final String aggregateId,
            final List<? extends TEvent> events,
            final ExpectedVersion expectedVersion) {
        return update(aggregateId, events, expectedVersion, null);
    }

    /**
     * Updates a given existing or new aggregate with single event.
     *
     * @param aggregateId     The id of the aggregate to update.
     * @param event           The list of event to update the aggregate with.
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
    default int update(
            final String aggregateId,
            final TEvent event,
            final ExpectedVersion expectedVersion,
            final Map<String, String> metadata) {
        return update(aggregateId, ImmutableList.of(event), expectedVersion, metadata);
    }

    /**
     * @param aggregateId     The id of the aggregate to update.
     * @param event           The list of event to update the aggregate with.
     * @param expectedVersion The expected version of the aggregate at the time the new events are
     *                        appended to its underlying stream of events. If the version at the
     *                        time the append is attempted differs from the one provided, an
     *                        UnexpectedVersionException will be thrown.
     */
    default int update(
            final String aggregateId, final TEvent event, final ExpectedVersion expectedVersion) {
        return update(aggregateId, ImmutableList.of(event), expectedVersion, null);
    }
}
