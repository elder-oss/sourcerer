package org.elder.sourcerer2.dbstore

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.elder.sourcerer2.EventData
import org.elder.sourcerer2.EventNormalizer
import org.elder.sourcerer2.EventRecord
import org.elder.sourcerer2.EventRepository
import org.elder.sourcerer2.EventSubscriptionUpdate
import org.elder.sourcerer2.ExpectedVersion
import org.elder.sourcerer2.RepositoryReadResult
import org.elder.sourcerer2.RepositoryVersion
import org.elder.sourcerer2.StreamId
import org.elder.sourcerer2.StreamReadResult
import org.elder.sourcerer2.StreamVersion
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory

internal class DbstoreEventRepository<T>(
        private val eventType: Class<T>,
        private val supportedShards: Int?,
        private val category: String,
        private val eventStore: DbstoreEventStore,
        private val objectMapper: ObjectMapper,
        private val normalizer: EventNormalizer<T>?
) : EventRepository<T> {
    override fun getShards(): Int? {
        return supportedShards
    }

    override fun getEventType(): Class<T> {
        return eventType
    }

    override fun readAll(
            version: RepositoryVersion?,
            shard: Int?,
            maxEvents: Int
    ): RepositoryReadResult<T>? {
        val dbstoreVersion = version?.toDbstoreRepositoryVersion()
        val eventRows = eventStore.readRepositoryEvents(
                category,
                shard,
                dbstoreVersion,
                maxEvents)

        return createReadResult(
                eventRows,
                version,
                "$category${if (shard != null) ":$shard" else ""}",
                { it.getRepositoryVersion().toRepositoryVersion() },
                { events, endOfStream, newVersion ->
                    RepositoryReadResult(
                            events,
                            newVersion,
                            endOfStream)
                }
        )
    }

    override fun read(
            streamId: StreamId,
            version: StreamVersion?,
            maxEvents: Int
    ): StreamReadResult<T>? {
        val dbstoreVersion = version?.toDbstoreStreamVersion()
        val eventRows = eventStore.readStreamEvents(streamId,
                category, dbstoreVersion, maxEvents)
        return createReadResult(
                eventRows,
                version,
                "$category:${streamId.identifier}",
                { it.getStreamVersion().toStreamVersion() },
                { events, endOfStream, newVersion ->
                    StreamReadResult(
                            events,
                            newVersion,
                            endOfStream)
                }
        )
    }

    override fun append(
            streamId: StreamId,
            events: MutableList<EventData<T>>,
            version: ExpectedVersion?
    ): StreamVersion {
        try {
            val newVersion = eventStore.appendStreamEvents(
                    streamId,
                    category,
                    version?.toExpectExisting(),
                    version?.toExpectVersion(),
                    events.map { toDbstoreEventData(streamId, it) }
            )
            return newVersion.toStreamVersion()
        } catch (ex: DbstoreUnexpectedVersionException) {
            TODO()
        }
    }

    private fun toDbstoreEventData(
            streamId: StreamId,
            eventData: EventData<T>
    ): DbstoreEventData {
        return DbstoreEventData(
                streamId = streamId,
                category = category,
                eventId = eventData.eventId,
                eventType = eventData.eventType,
                data = serializeEvent(eventData.event),
                metadata = serializeMetadata(eventData.metadata)
        )
    }

    private fun serializeMetadata(metadata: Map<String, String>): String {
        // TODO: Handle errors more nicely
        return objectMapper.writeValueAsString(metadata)
    }

    private fun serializeEvent(event: T): String {
        // TODO: Handle errors more nicely
        return objectMapper.writeValueAsString(event)
    }

    private fun fromDbstoreEventRecord(record: DbstoreEventRecord): EventRecord<T> {
        return EventRecord(
                eventId = record.eventId,
                streamId = record.streamId,
                streamVersion = record.getStreamVersion().toStreamVersion(),
                repositoryVersion = record.getRepositoryVersion().toRepositoryVersion(),
                eventType = record.eventType,
                timestamp = record.timestamp,
                metadata = parseMetadata(record.metadata),
                event = parseEvent(record.data)
        )
    }

    private fun parseEvent(data: String): T {
        // TODO: Handle errors more nicely
        val rawEvent = objectMapper.readValue(data, eventType)
        return normalizeEvent(rawEvent)
    }

    private fun parseMetadata(metadata: String): ImmutableMap<String, String> {
        // TODO: Handle errors more nicely
        return objectMapper.readValue(metadata, object : TypeReference<Map<String, String>>() {})
    }

    private fun <V, R> createReadResult(
            eventRows: List<DbstoreEventRow>,
            version: V?,
            streamIdentifier: String,
            versionExtractor: (DbstoreEventRecord) -> V,
            resultCreator: (ImmutableList<EventRecord<T>>, Boolean, V) -> R
    ): R? {
        val events = mutableListOf<EventRecord<T>>()
        var lastVersion: V? = null
        var foundEndOfStream = false

        scanrows@ for (row in eventRows) {
            when (row) {
                is DbstoreEventRow.Event -> {
                    events.add(fromDbstoreEventRecord(row.eventData))
                    lastVersion = versionExtractor(row.eventData)
                }
                is DbstoreEventRow.EndOfStream -> {
                    foundEndOfStream = true
                    break@scanrows
                }
            }
        }

        return when {
            lastVersion != null -> {
                // Happy path, we have read some rows - doesn't matter if we had a version specified or not, the new
                // version is the one of the last event read
                logger.debug(
                        "Read stream {} up to version {}",
                        streamIdentifier, lastVersion
                )
                resultCreator(ImmutableList.copyOf(events), foundEndOfStream, lastVersion)
            }
            foundEndOfStream && version != null -> {
                // We got no events but had a "from" specified, we assume the caller knows what they're talking
                // about (the version does exist in this stream) and there are no newer events, so the version
                //  specified is still relevant.
                logger.debug(
                        "Read {} - with no new events after {}",
                        streamIdentifier, lastVersion
                )

                resultCreator(ImmutableList.copyOf(events), foundEndOfStream, version)
            }
            else -> {
                // We have read no events, and found no end of stream marker, so the stream can't exist.
                // Check for some potential weird cases that should not happen, e.g. we have an end of stream marker
                // but no events.
                if (foundEndOfStream) {
                    logger.error(
                            "Read {} from beginning and found sentinel but no events!",
                            streamIdentifier
                    )
                }

                logger.debug(
                        "Read {} but found nothing",
                        streamIdentifier, lastVersion
                )
                null
            }
        }
    }

    override fun getStreamPublisher(streamId: StreamId, fromVersion: StreamVersion?): Publisher<EventSubscriptionUpdate<T>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getRepositoryPublisher(
            fromVersion: RepositoryVersion?,
            shard: Int?
    ): Publisher<EventSubscriptionUpdate<T>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun normalizeEvent(rawEvent: T): T {
        return if (normalizer != null) {
            normalizer.normalizeEvent(rawEvent)
        } else {
            rawEvent
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DbstoreEventRepository::class.java)
    }
}

private fun ExpectedVersion.toExpectExisting(): Boolean? {
    return when (this) {
        is ExpectedVersion.Exactly -> true
        ExpectedVersion.NotCreated -> false
        ExpectedVersion.AnyExisting -> true
        ExpectedVersion.Any -> null
    }
}

private fun ExpectedVersion.toExpectVersion(): DbstoreStreamVersion? {
    return when (this) {
        is ExpectedVersion.Exactly -> streamVersion.toDbstoreStreamVersion()
        ExpectedVersion.NotCreated,
        ExpectedVersion.AnyExisting,
        ExpectedVersion.Any -> null
    }
}
