package org.elder.sourcerer2

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.util.UUID

/**
 * Globally unique identifier of an event. This id does not have any defined order when compared
 * with other events, but will be globally unique across all events and repositories. The event id
 * is created by the application that creates the event - not sourcerer or the persistence engine
 * - to facilitate de-duping and idempotency. A persistence engine _may_ choose to accept events
 * silently if the same events (as identified by the id) are attempted to be written twice to allow
 * for silent replays.
 */
data class EventId @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
        @get:JsonValue
        val identifier: UUID
) {
    companion object {
        @JvmStatic
        fun newUniqueId(): EventId {
            return EventId(UUID.randomUUID())
        }

        @JvmStatic
        fun ofUuid(uuid: UUID): EventId {
            return EventId(uuid)
        }
    }
}
