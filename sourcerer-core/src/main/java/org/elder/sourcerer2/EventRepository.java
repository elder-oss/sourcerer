package org.elder.sourcerer2;

import kotlinx.coroutines.channels.ReceiveChannel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface EventRepository<T> {
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
     * @param shard     The shard to read from. If specified, events are limited to the specified
     *                  logical shard. It is up to the subscriber to specify how the repository
     *                  is to be divided up through the shard count parameter in the shard
     *                  argument.
     * @param maxEvents The maximum number of events to read in one go. Note that this may be
     *                  truncated to a lower number by the implementation, it is not safe to assume
     *                  that a successful read will have this many events, even if they are present
     *                  in the underlying event store.
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no events exist in this repository.
     */
    RepositoryReadResult<T> readAll(
            RepositoryVersion version,
            RepositoryShard shard,
            int maxEvents);

    /**
     * Reads from the stream of all events kept in the event repository.
     *
     * @param version The position in the event stream to read events after. When reading from
     *                an event stream in batches, this would be the version returned from the last
     *                read event, not the version expected to be read next. Use null to read the
     *                stream from the very beginning.
     * @param shard   The shard to read from. If specified, events are limited to the specified
     *                logical shard. It is up to the subscriber to specify how the repository
     *                is to be divided up through the shard count parameter in the shard
     *                argument.
     * @return A result record describing the outcome of the read and the events themselves, or null
     * if no events exist in this repository.
     */
    default RepositoryReadResult<T> readAll(
            final RepositoryVersion version,
            RepositoryShard shard
    ) {
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
     * Gets a ReceiveChannel that receives events from the repository (and the specific shard
     * if specified). Events will be produced starting with the given version (or the beginning of
     * the repository). If the subscription is no longer used, it should be cancelled by the
     * caller.
     *
     * @param fromVersion The version to start subscribing from (exclusive). This would normally be
     *                    the version that has last been processed - not the version that is
     *                    expected to be returned as the first event. Use null to subscribe from
     *                    the beginning of the stream. If less than the current version, the
     *                    publisher will start replaying historical events, and then
     *                    transparently switch to live events.
     * @param shard       The shard to read from. If specified, events are limited to the specified
     *                    logical shard. It is up to the subscriber to specify how the repository
     *                    is to be divided up through the shard count parameter in the shard
     *                    argument.
     * @param batchSize   A hint as to the number of events to read in one go. This may be used to
     *                    control the amount of events read for the catch up phase of a subscription
     *                    and/or the real time one.
     * @return A channel that will receive events from the repository. The channel should be
     * cancelled when no longer used to free up underlying resources.
     */
    @NotNull
    ReceiveChannel<EventSubscriptionUpdate<T>> subscribe(
            RepositoryVersion fromVersion,
            RepositoryShard shard,
            int batchSize
    );
}
