package org.elder.sourcerer2

import com.google.common.collect.ImmutableMap

/**
 * Event payload with metadata and related information, for writing events.
 *
 * @param <T> The (base) type of events wrapped.
 */
data class EventData<T>(
        val eventId: EventId,
        val eventType: String,
        val metadata: ImmutableMap<String, String>,
        val event: T
)
