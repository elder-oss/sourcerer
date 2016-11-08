package org.elder.sourcerer;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Event event with metadata and related information, as read from an event repository.
 *
 * @param <T> The (base) type of events wrapped.
 */
public final class EventRecord<T> {
    @NotNull
    private final String streamId;
    private final int streamVersion;
    private final int aggregateVersion;
    @NotNull
    private final String eventType;
    @NotNull
    private final UUID eventId;
    @NotNull
    private final Instant timestamp;
    @NotNull
    private final ImmutableMap<String, String> metadata;
    @NotNull
    private final T event;

    public EventRecord(
            @NotNull final String streamId,
            final int streamVersion,
            final int aggregateVersion,
            @NotNull final String eventType,
            @NotNull final UUID eventId,
            @NotNull final Instant timestamp,
            @NotNull final ImmutableMap<String, String> metadata,
            final T event) {
        this.streamId = streamId;
        this.streamVersion = streamVersion;
        this.aggregateVersion = aggregateVersion;
        this.eventType = eventType;
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.metadata = metadata;
        this.event = event;
    }

    @NotNull
    public String getStreamId() {
        return streamId;
    }

    /**
     * Gets the version (event sequence number) for the given event stream. This number relates to
     * the position of the events in the stream subscribed to or read from, which in the case of
     * projections / aggregate streams, may be different from the version relative to the stream
     */
    public int getStreamVersion() {
        return streamVersion;
    }

    /**
     * Gets the version (event sequence number) for the aggregate that the event relates to, i.e.
     * the original stream that the event was written to, as apposed to the event number on the
     * stream it was read from. In the case of aggregate streams / projections, this may differ
     * from the stream version.
     */
    public int getAggregateVersion() {
        return aggregateVersion;
    }

    @NotNull
    public String getEventType() {
        return eventType;
    }

    @NotNull
    public UUID getEventId() {
        return eventId;
    }

    @NotNull
    public Instant getTimestamp() {
        return timestamp;
    }

    @NotNull
    public ImmutableMap<String, String> getMetadata() {
        return metadata;
    }

    @NotNull
    public T getEvent() {
        return event;
    }

    @Override
    public String toString() {
        return "EventRecord{" +
                "streamId='" + streamId + '\'' +
                ", streamVersion=" + streamVersion +
                ", eventType='" + eventType + '\'' +
                ", eventId=" + eventId +
                ", metadata=" + metadata +
                ", event=" + event +
                '}';
    }
}
