package org.elder.sourcerer2.jdbc

import org.elder.sourcerer2.EventId
import org.elder.sourcerer2.StreamId
import java.time.Instant

/**
 * Direct typed representation of the data read from the database for a particular row
 */
data class JdbcEventRecord(
        /**
         * The id of the stream that this event relates to. This id is unique only within a
         * particular repository, e.g. only across events describing the same type of aggregate.
         */
        val streamId: StreamId,

        /**
         * The category of events that this event is representing (e.g. the type of aggregates that it represents).
         */
        val category: String,

        /**
         * An integer shard 0-1023, based on the stream id. This can be used to route events in subscriptions to
         * particular instances.
         */
        val shard: Int,

        /**
         * The time at which the storage engine recorded the event. This may be different from the
         * time of the actual business event that the event describes. It should not be relied on to
         * compare order of events, use stream or repository version instead.
         */
        val timestamp: Instant,

        /**
         * The sequence number (starting with 0) within a transaction, used to enforce order, as all of these events
         * will have the same exact timestamp.
         */
        val transactionSeqNr: Int,

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
        val metadata: Map<String, String>
)
