package org.elder.sourcerer2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.List;

public interface EventRepository<T> {
    /**
     * Gets the number of distinct shards supported by this event repository. Shard allow for all
     * events for a category to be read or subscribed to in parallel streams, where each individual
     * event stream is always mapped to the same shard. If this returns null, then shards are not
     * supported.
     */
    @Nullable
    Integer getShards();

    /**
     * Gets the java Class representing the runtime type that this event repository manages events
     * for.
     *
     * @return A class representing the root event type for events in this repository.
     */
    @NotNull
    Class<T> getEventType();

    /**
     * Reads from the stream of all events kept in the event repository.
     *
     * @param version   The position in the event stream to read events after. When reading from
     *                  an event stream in batches, this would be the version returned from the last
     *                  read event, not the version expected to be read next. Use null to read the
     *                  stream from the very beginning.
     * @param shard     The shard to read from. If specified, this must be in the range from 0
     *                  (inclusive) to the value returned by getShards() (exclusive)
     * @param maxEvents The maximum number of events to read in one go. Note that this may be
     *                  truncated to a lower number by the implementation, it is not safe to assume
     *                  that a successful read will have this many events, even if they are present
     *                  in the underlying event store.
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no events exist in this repository.
     */
    RepositoryReadResult<T> readAll(
            RepositoryVersion version,
            Integer shard,
            int maxEvents);

    /**
     * Reads from the stream of all events kept in the event repository.
     *
     * @param version The position in the event stream to read events after. When reading from
     *                an event stream in batches, this would be the version returned from the last
     *                read event, not the version expected to be read next. Use null to read the
     *                stream from the very beginning.
     * @param shard   The shard to read from. If specified, this must be in the range from 0
     *                (inclusive) to the value returned by getShards() (exclusive)
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no events exist in this repository.
     */
    default RepositoryReadResult<T> readAll(final RepositoryVersion version, Integer shard) {
        return readAll(version, shard, Integer.MAX_VALUE);
    }

    /**
     * Reads from the stream of all events kept in the event repository.
     *
     * @param version The position in the event stream to read events after. When reading from
     *                an event stream in batches, this would be the version returned from the last
     *                read event, not the version expected to be read next. Use null to read the
     *                stream from the very beginning.
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no events exist in this repository.
     */
    default RepositoryReadResult<T> readAll(final RepositoryVersion version) {
        return readAll(version, null, Integer.MAX_VALUE);
    }

    /**
     * Reads from the stream of all events kept in the event repository.
     *
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no events exist in this repository.
     */
    default RepositoryReadResult<T> readAll() {
        return readAll(null, null);
    }

    /**
     * Reads all events for a given stream id from a given version and onwards.
     *
     * @param streamId  The id of the stream to read events for.
     * @param version   The position in the event stream to read events after. When reading from
     *                  an event stream in batches, this would be the version returned from the last
     *                  read event, not the version expected to be read next. Use null to read the
     *                  stream from the very beginning.
     * @param maxEvents The maximum number of events to read in one go. Note that this may be
     *                  truncated to a lower number by the implementation, it is not safe to assume
     *                  that a successful read will have this many events, even if they are present
     *                  in the underlying event store.
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no stream was found.
     */
    StreamReadResult<T> read(@NotNull StreamId streamId, StreamVersion version, int maxEvents);

    /**
     * Reads all events for a given stream id from a given version and onwards, with no specified
     * upper bound on the number of events returned.
     *
     * @param streamId The id of the stream to read events for.
     * @param version  The version to read events from. The version is an opaque id
     *                 pointing to a logical point-in-time for the event stream. Specify null
     *                 to read from the beginning of the stream.
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no stream was found.
     */
    default StreamReadResult<T> read(
            @NotNull final StreamId streamId, final StreamVersion version) {
        return read(streamId, version, Integer.MAX_VALUE);
    }

    /**
     * Reads all events for a given stream id from the beginning.
     *
     * @param streamId The id of the stream to read events for.
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no stream was found.
     */
    default StreamReadResult<T> read(@NotNull final StreamId streamId) {
        return read(streamId, null);
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
    @NotNull
    StreamVersion append(
            @NotNull StreamId streamId,
            @NotNull List<EventData<T>> events,
            ExpectedVersion version);

    /**
     * Gets a Publisher that can be used to subscribe to events for a given stream id.
     *
     * @param streamId    The id of the stream to subscribe to.
     * @param fromVersion The version to start subscribing from (exclusive). This would normally be
     *                    the version that has last been processed - not the version that is
     *                    expected to be returned as the first event. Use null to subscribe from
     *                    the beginning of the stream. If less than the current version, the
     *                    publisher will start replaying historical events, and then
     *                    transparently switch to live events.
     * @return A Publisher that, when subscribed to, will start producing events for each new event
     * written to the given stream id.
     */
    @NotNull
    Publisher<EventSubscriptionUpdate<T>> getStreamPublisher(
            @NotNull StreamId streamId,
            StreamVersion fromVersion);

    /**
     * Gets a Publisher that can be used to subscribe to all events related to this repository.
     *
     * @param fromVersion The version to start subscribing from (exclusive). This would normally be
     *                    the version that has last been processed - not the version that is
     *                    expected to be returned as the first event. Use null to subscribe from
     *                    the beginning of the stream. If less than the current version, the
     *                    publisher will start replaying historical events, and then
     *                    transparently switch to live events.
     * @param shard       The shard to read from. If specified, this must be in the range from 0
     *                    (inclusive) to the value returned by getShards() (exclusive)
     * @return A Publisher that, when subscribed to, will start producing events for each new event
     * written to any stream related to this repository.
     */
    @NotNull
    Publisher<EventSubscriptionUpdate<T>> getRepositoryPublisher(
            RepositoryVersion fromVersion,
            Integer shard);
}
