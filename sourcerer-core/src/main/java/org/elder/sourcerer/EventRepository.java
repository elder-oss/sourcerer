package org.elder.sourcerer;

import org.reactivestreams.Publisher;

import java.util.List;

public interface EventRepository<T> {
    /**
     * Reads all events for a given stream id from a given version and onwards.
     *
     * @param streamId  The id of the stream to read events for.
     * @param version   The version to read events from. Versions are monotonically increasing
     *                  starting with 0, specifying a version of 0 is equivalent to reading the
     *                  events from the beginning of the stream.
     * @param maxEvents The maximum number of events to read in one go. Note that this may be
     *                  truncated to a lower number by the implementation, it is not safe to assume
     *                  that a successful read will have this many events, even if they are present
     *                  in the underlying event store.
     * @return An immutable list of all events for a given stream, each event annotated with
     * additional information such as its version, unique id, and metadata - or null if no stream
     * with the given id is found.
     */
    EventReadResult<T> read(String streamId, int version, int maxEvents);

    /**
     * Reads all events for a given stream id from a given version and onwards, with no specified
     * upper bound on the number of events returned.
     *
     * @param streamId The id of the stream to read events for.
     * @param version  The version to read events from. Versions are monotonically increasing
     *                 starting with 0, specifying a version of 0 is equivalent to reading the
     *                 events from the beginning of the stream.
     * @return An immutable list of all events for a given stream, each event annotated with
     * additional information such as its version, unique id, and metadata - or null if no stream
     * with the given id is found.
     */
    default EventReadResult<T> read(final String streamId, final int version) {
        return read(streamId, version, Integer.MAX_VALUE);
    }

    /**
     * Reads all events for a given stream id from the beginning.
     *
     * @param streamId The id of the stream to read events for.
     * @return An immutable list of all events for a given stream, each event annotated with
     * additional information such as its version, unique id, and metadata - or null if no stream
     * with the given id is found.
     */
    default EventReadResult<T> read(final String streamId) {
        return read(streamId, 0);
    }

    /**
     * Appends events to a new or existing stream.
     *
     * @param streamId The stream to append events to.
     * @param events   The sequence of events to append, with annotations such as event id and
     *                 metadata for each.
     * @param version  An instance of ExpectedVersion describing the expected state of the stream.
     *                 If the current version does not match the expected one at the time that the
     *                 events are attempted to be added, an UnexpectedVersionException is thrown.
     * @return The current version of the stream after the append has completed.
     */
    int append(String streamId, List<EventData<T>> events, ExpectedVersion version);

    /**
     * Gets a Publisher that can be used to subscribe to events for a given stream id.
     *
     * @param streamId    The id of the stream to subscribe to.
     * @param fromVersion The version to start subscribing from (exclusive) starting from 0. Use
     *                    null to subscribe from the beginning of the stream, passing 0 will return
     *                    events from version 1 (not the beginning). If less than the current
     *                    version, the publisher will start replaying historical events, and then
     *                    transparently switch to live events.
     * @return A Publisher that, when subscribed to, will start producing events for each new event
     * written to the given stream id.
     */
    Publisher<EventSubscriptionUpdate<T>> getStreamPublisher(String streamId, Integer fromVersion);

    /**
     * Gets a Publisher that can be used to subscribe to all events related to this repository.
     *
     * @param fromVersion The version to start subscribing from (exclusive) starting from 0. Use
     *                    null to subscribe from the beginning of the stream, passing 0 will return
     *                    events from version 1 (not the beginning). If less than the current
     *                    version, the publisher will start replaying historical events, and then
     *                    transparently switch to live events.
     * @return A Publisher that, when subscribed to, will start producing events for each new event
     * written to any stream related to this repository.
     */
    Publisher<EventSubscriptionUpdate<T>> getPublisher(Integer fromVersion);
}
