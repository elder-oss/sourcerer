package org.elder.sourcerer2.dbstore

import org.elder.sourcerer2.EventId
import org.elder.sourcerer2.StreamId

/**
 * Generic representation of data to store for a particular event.
 */
data class DbstoreEventData(
        /**
         * Gets a globally unique id for this event. This will be unique across all events and all
         * repositories.
         */
        val eventId: EventId,

        /**
         * The application provided type of this particular event.
         */
        val eventType: String,

        /**
         * The event data itself, stored as a JSON string.
         */
        val data: String,

        /**
         * Other information about the event as provided when the event was persisted.
         */
        val metadata: String
)
