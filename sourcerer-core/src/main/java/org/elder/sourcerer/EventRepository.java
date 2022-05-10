package org.elder.sourcerer;

import org.reactivestreams.Publisher;

import java.util.List;

public interface EventRepository<T> {
    /**
     * Gets the java Class representing the runtime type that this event repository manages events
     * for.
     *
     * @return A class representing the root event type for events in this repository.
     */
    Class<T> getEventType();

    /**
     * Reads from the stream of all events kept in the event repository.
     *
     * @param version   The version to read events from. Versions are monotonically increasing
     *                  starting with 0, specifying a version of 0 is equivalent to reading the
     *                  events from the beginning of the stream.
     * @param maxEvents The maximum number of events to read in one go. Note that this may be
     *                  truncated to a lower number by the implementation, it is not safe to assume
     *                  that a successful read will have this many events, even if they are present
     *                  in the underlying event store.
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no events exist in this repository.
     */
    EventReadResult<T> readAll(int version, int maxEvents);

    /**
     * Reads from the stream of all events kept in the event repository.
     *
     * @param version The version to read events from. Versions are monotonically increasing
     *                starting with 0, specifying a version of 0 is equivalent to reading the events
     *                from the beginning of the stream.
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no events exist in this repository.
     */
    default EventReadResult<T> readAll(final int version) {
        return readAll(version, Integer.MAX_VALUE);
    }

    /**
     * Reads from the stream of all events kept in the event repository.
     *
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no events exist in this repository.
     */
    default EventReadResult<T> readAll() {
        return readAll(0);
    }

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
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no stream was found.
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
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no stream was found.
     */
    default EventReadResult<T> read(final String streamId, final int version) {
        return read(streamId, version, Integer.MAX_VALUE);
    }

    /**
     * Reads all events for a given stream id from the beginning.
     *
     * @param streamId The id of the stream to read events for.
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no stream was found.
     */
    default EventReadResult<T> read(final String streamId) {
        return read(streamId, 0);
    }

    /**
     * Reads first event for a given stream id.
     *
     * @param streamId  The id of the stream to read event for.
     * @return The event, or null if no stream was found.
     */
    EventRecord<T> readFirst(String streamId);

    /**
     * Reads last event for a given stream id.
     *
     * @param streamId  The id of the stream to read event for.
     * @return The event, or null if no stream was found.
     */
    EventRecord<T> readLast(String streamId);

    /**
     * Gets the current version (i.e. the position of the last written event) for the event
     * repository.
     *
     * @return The position of the last event written to the event repository, 0 based. Returns -1
     * if no events are available.
     */
    int getCurrentVersion();

    /**
     * Gets the current version (i.e. the position of the last written event) for a given stream.
     *
     * @param streamId The id of the stream to read events for.
     * @return The position of the last event written to the specified stream, 0 based. Returns -1
     * if no events are available.
     */
    int getCurrentVersion(String streamId);

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
    Publisher<EventSubscriptionUpdate<T>>
            getStreamPublisher(String streamId, Integer fromVersion);

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
