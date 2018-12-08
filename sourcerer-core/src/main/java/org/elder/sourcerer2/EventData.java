package org.elder.sourcerer2;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.UUID;

/**
 * Event payload with metadata and related information, for writing events.
 *
 * @param <T> The (base) type of events wrapped.
 */
public final class EventData<T> {
    private final String eventType;
    private final EventId eventId;
    private final ImmutableMap<String, String> metadata;
    private final T payload;

    public EventData(
            final String eventType,
            final EventId eventId,
            final Map<String, String> metadata,
            final T payload) {
        this.eventType = eventType;
        this.eventId = eventId;
        this.metadata = metadata == null ? null : ImmutableMap.copyOf(metadata);
        this.payload = payload;
    }

    public String getEventType() {
        return eventType;
    }

    public EventId getEventId() {
        return eventId;
    }

    public ImmutableMap<String, String> getMetadata() {
        return metadata;
    }

    public T getEvent() {
        return payload;
    }

    @Override
    public String toString() {
        return "EventData{" +
                "eventType='" + eventType + '\'' +
                ", eventId=" + eventId +
                ", metadata=" + metadata +
                ", payload=" + payload +
                '}';
    }
}
