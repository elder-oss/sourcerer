package org.elder.sourcerer2.dbstore

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.elder.sourcerer2.EventData
import org.elder.sourcerer2.EventRecord
import org.elder.sourcerer2.EventRepository
import org.elder.sourcerer2.EventSubscriptionUpdate
import org.elder.sourcerer2.ExpectedVersion
import org.elder.sourcerer2.RepositoryReadResult
import org.elder.sourcerer2.RepositoryVersion
import org.elder.sourcerer2.StreamId
import org.elder.sourcerer2.StreamReadResult
import org.elder.sourcerer2.StreamVersion
import org.elder.sourcerer2.exceptions.UnexpectedVersionException
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux

internal class DbstoreEventRepository<T>(
        private val repositoryInfo: DbstoreRepositoryInfo<T>,
        private val eventStore: DbstoreEventStore,
        private val objectMapper: ObjectMapper
) : EventRepository<T> {
    override fun getShards(): Int? {
        return repositoryInfo.shards
    }

    override fun getEventType(): Class<T> {
        return repositoryInfo.eventType
    }

    override fun readAll(
            version: RepositoryVersion?,
            shard: Int?,
            maxEvents: Int
    ): RepositoryReadResult<T>? {
        val shardRange = validateShard(shard)
        val dbstoreVersion = version?.toDbstoreRepositoryVersion()
        val eventRows = eventStore.readRepositoryEvents(
                repositoryInfo,
                dbstoreVersion,
                shardRange,
                maxEvents)

        return createReadResult(
                eventRows,
                version,
                "$repositoryInfo${if (shard != null) ":$shard" else ""}",
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
        val eventRows = eventStore.readStreamEvents(
                repositoryInfo,
                streamId,
                dbstoreVersion,
                maxEvents
        )
        return createReadResult(
                eventRows,
                version,
                "${repositoryInfo.repository}:${streamId.identifier}",
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
                    repositoryInfo,
                    streamId,
                    version?.toExpectExisting(),
                    version?.toExpectVersion(),
                    events.map { it.toDbstoreEventData() }
            )
            return newVersion.toStreamVersion()
        } catch (ex: DbstoreUnexpectedVersionException) {
            when (ex) {
                is NotFoundWhenExpectedException -> throw UnexpectedVersionException(
                        "Stream was not found when expected to exist",
                        null,
                        version
                )
                is FoundWhenNotExpectedException -> throw UnexpectedVersionException(
                        "Stream was found when expected not to exist",
                        ex.currentVersion?.toStreamVersion(),
                        version
                )
                is FoundWithDifferentVersionException -> throw UnexpectedVersionException(
                        "Stream was found with conflicting version",
                        ex.currentVersion?.toStreamVersion(),
                        version
                )
            }
        }
    }

    private fun validateShard(shard: Int?): DbstoreShardHashRange {
        return if (shard != null) {
            if (shard < 0) {
                throw IllegalArgumentException("Shard nunmber must be 0 or greater")
            }
            if (shard >= repositoryInfo.shards) {
                throw IllegalArgumentException(
                        "Shard number must be less than the configured number of shards: ${repositoryInfo.shards}")
            }
            DbstoreSharder.getShardRange(shard, repositoryInfo.shards)
        } else {
            DbstoreShardHashRange.COMPLETE_RANGE
        }
    }

    private fun EventData<T>.toDbstoreEventData(): DbstoreEventData {
        return DbstoreEventData(
                eventId = eventId,
                eventType = eventType,
                data = serializeEvent(event),
                metadata = serializeMetadata(metadata)
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

    private fun parseMetadata(metadataStr: String): ImmutableMap<String, String> {
        // TODO: Handle errors more nicely
        val metadata = objectMapper.readValue<Map<String, String>>(
                metadataStr,
                object : TypeReference<Map<String, String>>() {}
        )
        return ImmutableMap.copyOf(metadata)
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
        Flux.create()
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun normalizeEvent(rawEvent: T): T {
        return if (repositoryInfo.normalizer != null) {
            repositoryInfo.normalizer.normalizeEvent(rawEvent)
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
