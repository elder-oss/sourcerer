package org.elder.sourcerer2

import com.google.common.collect.ImmutableMap
import java.time.Instant

/**
 * Event event with metadata and related information, as read from an event repository.
 *
 * @param <T> The (base) type of events wrapped.
 */
data class EventRecord<T>(
        /**
         * Gets a globally unique id for this event. This will be unique across all events and all
         * repositories.
         */
        val eventId: EventId,

        /**
         * The id of the stream that this event relates to. This id is unique only within a
         * particular repository, e.g. only across events describing the same type of aggregate.
         */
        val streamId: StreamId,

        /**
         * Gets the position of this event in the individual event stream that it was originally
         * written to (regardless of whether this came from reading that stream directly or from
         * subscribing to a repository as a whole.
         */
        val streamVersion: StreamVersion,

        /**
         * Gets the position of this event as it relates to others in the repository it was read
         * from, e.g. in relation to all other events representing aggregates of the same type.
         */
        val repositoryVersion: RepositoryVersion,

        /**
         * The application provided type of this particular event.
         */
        val eventType: String,

        /**
         * The time at which the storage engine recorded the event. This may be different from the
         * time of the actual business event that the event describes. It should not be relied on to
         * compare order of events, use stream or repository version instead.
         */
        val timestamp: Instant,

        /**
         * Other information about the event as provided when the event was persisted.
         */
        val metadata: ImmutableMap<String, String>,

        /**
         * The event data itself.
         */
        val event: T
)
