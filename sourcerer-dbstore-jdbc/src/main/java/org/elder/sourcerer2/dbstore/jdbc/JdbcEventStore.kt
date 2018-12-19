package org.elder.sourcerer2.dbstore.jdbc

import org.elder.sourcerer2.EventId
import org.elder.sourcerer2.StreamId
import org.elder.sourcerer2.dbstore.DbstoreEventData
import org.elder.sourcerer2.dbstore.DbstoreEventRecord
import org.elder.sourcerer2.dbstore.DbstoreEventRow
import org.elder.sourcerer2.dbstore.DbstoreEventStore
import org.elder.sourcerer2.dbstore.DbstoreRepositoryInfo
import org.elder.sourcerer2.dbstore.DbstoreRepositoryVersion
import org.elder.sourcerer2.dbstore.DbstoreShardHashRange
import org.elder.sourcerer2.dbstore.DbstoreSharder
import org.elder.sourcerer2.dbstore.DbstoreStreamVersion
import org.elder.sourcerer2.dbstore.FoundWhenNotExpectedException
import org.elder.sourcerer2.dbstore.FoundWithDifferentVersionException
import org.elder.sourcerer2.dbstore.NotFoundWhenExpectedException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/**
 * Event storage backend using a generic JDBC driver. Note: This is created to allow for local development and
 * testing of the Sourcerer dbstore event repository, and will follow the behaviour of the Spanner backend as closely
 * as possible, rather than being the most performant implementation. Specifically, this comes out in the use of
 * timestamps. While Spanner provides monotonically increasing commit timestamps, a generic SQL backend does not. As
 * such, we need to emulate this behaviour by using locking reads.
 */
class JdbcEventStore(
        private val dataSource: DataSource,
        eventsTableName: String,
        private val maxReadBatchSize: Int = 2048
) : DbstoreEventStore {
    override fun readRepositoryEvents(
            repositoryInfo: DbstoreRepositoryInfo<*>,
            fromVersion: DbstoreRepositoryVersion?,
            shardRange: DbstoreShardHashRange,
            maxEvents: Int
    ): List<DbstoreEventRow> {
        val effectiveMaxEvents = getMaxReadBatchSize(maxEvents)
        return withConnection(true) { connection ->
            val readStatement = connection.createReadRepositoryStatement(
                    repositoryInfo = repositoryInfo,
                    shardRange = shardRange,
                    effectiveMaxEvents = effectiveMaxEvents,
                    fromVersion = fromVersion
            )

            readStatement.executeQuery().use {
                it.readEventRowResults()
            }
        }
    }

    override fun readStreamEvents(
            repositoryInfo: DbstoreRepositoryInfo<*>,
            streamId: StreamId,
            fromVersion: DbstoreStreamVersion?,
            maxEvents: Int
    ): List<DbstoreEventRow> {
        val effectiveMaxEvents = getMaxReadBatchSize(maxEvents)
        return withConnection(true) { connection ->
            connection.readStreamEvents(
                    repositoryInfo = repositoryInfo,
                    streamId = streamId,
                    effectiveMaxEvents = effectiveMaxEvents,
                    fromVersion = fromVersion
            )
        }
    }

    override fun appendStreamEvents(
            repositoryInfo: DbstoreRepositoryInfo<*>,
            streamId: StreamId,
            expectExisting: Boolean?,
            expectVersion: DbstoreStreamVersion?,
            events: List<DbstoreEventData>
    ): DbstoreStreamVersion {
        // If we have an expected version, go look for the event after that point (or the sentinel if not found),
        // if we don't, then just look for the sentinel directly.
        return withConnection(false) { connection ->
            val fromVersion = expectVersion ?: SENTINEL_VERSION
            val rowsFromExpectedEnd = connection.readStreamEvents(
                    repositoryInfo = repositoryInfo,
                    streamId = streamId,
                    fromVersion = fromVersion,
                    effectiveMaxEvents = 1
            )
            expectExisting?.let { assertExistenceStatus(rowsFromExpectedEnd, it) }
            expectVersion?.let { assertExpectedVersion(rowsFromExpectedEnd, events[0].eventId) }

            val commitTimestamp = connection.claimCommitTimestamp(repositoryInfo)
            val streamHash = DbstoreSharder.getStreamHash(repositoryInfo, streamId)
            if (rowsFromExpectedEnd.isEmpty()) {
                // We have no existing string but have made it this far, which means that's OK.
                // Create a new one and burn in the shard.
                connection.insertSentinel(repositoryInfo, streamId, streamHash)
            }

            connection.persistEvents(
                    repositoryInfo = repositoryInfo,
                    streamId = streamId,
                    streamHash = streamHash,
                    commitTimestamp = commitTimestamp,
                    events = events
            )
        }
    }

    private fun assertExistenceStatus(
            rowsFromExpectedEnd: List<DbstoreEventRow>,
            expectExisting: Boolean
    ) {
        val existsNow = rowsFromExpectedEnd.isNotEmpty()

        if (existsNow) {
            if (!expectExisting) {
                val row = rowsFromExpectedEnd[0]
                val currentVersionIfKnown = when (row) {
                    is DbstoreEventRow.Event -> row.eventData.getStreamVersion()
                    is DbstoreEventRow.EndOfStream -> null
                }
                throw FoundWhenNotExpectedException(currentVersionIfKnown)
            }
        } else {
            if (expectExisting) {
                throw NotFoundWhenExpectedException()
            }
        }
    }

    private fun assertExpectedVersion(
            rowsFromExpectedEnd: List<DbstoreEventRow>,
            eventBeingAppended: EventId
    ) {
        // If we have an expected version, assert that the row we have is the sentinel, _or_ that it's representing
        // the write we're just about to make (idempotent write)
        val foundRow = rowsFromExpectedEnd[0]
        when (foundRow) {
            is DbstoreEventRow.EndOfStream -> {
                // All good, we're at the version expected, the next one is the end
            }
            is DbstoreEventRow.Event -> {
                // We've found some other event here, meaning someone else has managed to append events. Check if that
                // "someone else" is actually us, in which case that's OK!
                if (foundRow.eventData.eventId != eventBeingAppended) {
                    throw FoundWithDifferentVersionException(foundRow.eventData.getStreamVersion())
                }
            }
        }
    }

    private fun Connection.createReadRepositoryStatement(
            repositoryInfo: DbstoreRepositoryInfo<*>,
            shardRange: DbstoreShardHashRange,
            effectiveMaxEvents: Int,
            fromVersion: DbstoreRepositoryVersion?
    ): PreparedStatement {
        var i = 1
        val query = makeReadRepositoryEventsQuery(fromVersion != null)
        val statement = prepareStatement(query)
        statement.setInt(i++, shardRange.fromHashInclusive)
        statement.setInt(i++, shardRange.toHashExclusive - 1)
        statement.setString(i++, repositoryInfo.namespace)
        statement.setString(i++, repositoryInfo.repository)
        fromVersion?.let {
            statement.setTimestamp(i++, Timestamp.from(fromVersion.timestamp))
            statement.setTimestamp(i++, Timestamp.from(fromVersion.timestamp))
            statement.setString(i++, fromVersion.streamId.identifier)
            statement.setString(i++, fromVersion.streamId.identifier)
            statement.setInt(i++, fromVersion.transactionSequenceNr)

        }
        statement.setInt(i, effectiveMaxEvents)
        return statement
    }

    private fun Connection.readStreamEvents(
            repositoryInfo: DbstoreRepositoryInfo<*>,
            streamId: StreamId,
            effectiveMaxEvents: Int,
            fromVersion: DbstoreStreamVersion?
    ): List<DbstoreEventRow> {
        val readStatement = createReadStreamStatement(
                repositoryInfo,
                streamId,
                fromVersion,
                effectiveMaxEvents
        )

        return readStatement.executeQuery().use {
            it.readEventRowResults()
        }
    }

    private fun Connection.createReadStreamStatement(
            repositoryInfo: DbstoreRepositoryInfo<*>,
            streamId: StreamId,
            fromVersion: DbstoreStreamVersion?,
            effectiveMaxEvents: Int
    ): PreparedStatement {
        var i = 1
        val query = makeReadStreamEventsQuery(fromVersion != null)
        val statement = prepareStatement(query)
        val streamHash = DbstoreSharder.getStreamHash(repositoryInfo, streamId)
        statement.setInt(i++, streamHash)
        statement.setString(i++, repositoryInfo.namespace)
        statement.setString(i++, repositoryInfo.repository)
        statement.setString(i++, streamId.identifier)

        fromVersion?.let {
            statement.setTimestamp(i++, Timestamp.from(fromVersion.timestamp))
            statement.setTimestamp(i++, Timestamp.from(fromVersion.timestamp))
            statement.setInt(i++, fromVersion.transactionSequenceNr)
        }

        statement.setInt(i, effectiveMaxEvents)
        return statement
    }

    private fun <T> withConnection(readOnly: Boolean, dbOperation: (Connection) -> T): T {
        return dataSource.connection.use {
            it.autoCommit = false
            var committed = false
            if (readOnly) {
                it.isReadOnly = true
                it.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
            } else {
                it.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            }
            try {
                val result = dbOperation(it)
                it.commit()
                committed = true
                result
            } finally {
                if (!readOnly && !committed) {
                    it.rollback()
                }
            }
        }
    }

    private fun getMaxReadBatchSize(requestedBatchSize: Int): Int {
        return minOf(requestedBatchSize, maxReadBatchSize)
    }

    private fun ResultSet.readEventRowResults(): List<DbstoreEventRow> {
        return this.readRowsWith(::readEventRow)
    }

    private fun readEventRow(row: ResultSet): DbstoreEventRow {
        var i = 1
        val streamHash = row.getInt(i++)
        val repository = row.getString(i++)
        val streamId = StreamId.ofString(row.getString(i++))
        val timestamp = row.getTimestamp(i++).toInstant()

        return when (timestamp) {
            SENTINEL_TIMESTAMP ->
                // This is the end of stream marker, only extract the bits relevant to it
                DbstoreEventRow.EndOfStream(
                        streamId = streamId,
                        streamHash = streamHash,
                        repository = repository
                )
            else -> {
                val transactionSeqNr = row.getInt(i++)
                val eventId = EventId.ofUuid(UUID.fromString(row.getString(i++)))
                val eventType = row.getString(i++)
                val data = row.getString(i++)
                val metadata = row.getString(i++)
                val event = DbstoreEventRecord(
                        streamId = streamId,
                        streamHash = streamHash,
                        repository = repository,
                        timestamp = timestamp,
                        transactionSeqNr = transactionSeqNr,
                        eventId = eventId,
                        eventType = eventType,
                        data = data,
                        metadata = metadata
                )
                DbstoreEventRow.Event(event)
            }
        }
    }

    private fun Connection.claimCommitTimestamp(repositoryInfo: DbstoreRepositoryInfo<*>): Timestamp {
        // NOTE: This is a locking operation and a bottleneck for event writes. A more intrinsic way to accomplish this
        // would be to use an auto increment column, however, we want this to work as closely as possible to the Spanner
        // backend and expect this to be used for testing/development - not production, so we are OK with paying the
        // price of this. This also requires transactions to use SERIALIZABLE to ensure that timestamps are indeed
        // unique within a repository
        val statement = prepareStatement(getCommitTimestampQuery)
        statement.setString(1, repositoryInfo.namespace)
        statement.setString(2, repositoryInfo.repository)

        val resultSet = statement.executeQuery()
        resultSet.next()
        return resultSet.getTimestamp(1)
    }

    private fun Connection.persistEvents(
            repositoryInfo: DbstoreRepositoryInfo<*>,
            streamId: StreamId,
            streamHash: Int,
            commitTimestamp: Timestamp,
            events: List<DbstoreEventData>
    ): DbstoreStreamVersion {
        var lastIdx = 0

        events.forEachIndexed { idx, eventData ->
            var i = 1
            val statement = prepareStatement(writeEventDataStatement)
            statement.setInt(i++, streamHash)
            statement.setString(i++, repositoryInfo.namespace)
            statement.setString(i++, repositoryInfo.repository)
            statement.setString(i++, streamId.identifier)
            statement.setTimestamp(i++, commitTimestamp)
            statement.setInt(i++, idx)
            statement.setString(i++, eventData.eventId.id.toString())
            statement.setString(i++, eventData.eventType)
            statement.setString(i++, eventData.data)
            statement.setString(i, eventData.metadata)
            statement.execute()
            lastIdx = idx
        }

        return DbstoreStreamVersion(commitTimestamp.toInstant(), lastIdx)
    }

    private fun Connection.insertSentinel(
            repositoryInfo: DbstoreRepositoryInfo<*>,
            streamId: StreamId,
            streamHash: Int
    ) {
        var i = 1
        val statement = prepareStatement(writeEventDataStatement)
        statement.setInt(i++, streamHash)
        statement.setString(i++, repositoryInfo.namespace)
        statement.setString(i++, repositoryInfo.repository)
        statement.setString(i++, streamId.identifier)
        statement.setTimestamp(i++, Timestamp.from(SENTINEL_TIMESTAMP))
        statement.setInt(i++, 0)
        statement.setString(i++, UUID.randomUUID().toString())
        statement.setString(i++, "<end of stream>")
        statement.setString(i++, "")
        statement.setString(i, "")
        statement.execute()
    }

    private val getCommitTimestampQuery = """
        SELECT COALESCE(
          GREATEST(CURRENT_TIMESTAMP(6), TIMESTAMPADD(MICROSECOND, 1, MAX(timestamp))),
          CURRENT_TIMESTAMP(6)) AS commit_timestamp
        FROM $eventsTableName
        WHERE timestamp < '2038-01-19 03:14:07'
        AND namespace = ?
        AND repository = ?
        """.trimIndent()

    private val readEventDataPrelude = """
        SELECT stream_hash, repository, stream_id,
            timestamp, transaction_seq_nr, event_id, event_type, data, metadata
        FROM $eventsTableName
        """.trimIndent()

    private val filterRepositoryVersionFragment = """
        AND timestamp >= ?
        AND (timestamp > ? OR stream_id > ? OR (stream_id = ? AND transaction_seq_nr > ?))
        """.trimIndent()

    private fun makeReadRepositoryEventsQuery(queryVersion: Boolean) = """
        $readEventDataPrelude
        WHERE stream_hash BETWEEN ? AND ?
        AND namespace = ?
        AND repository = ?
        ${if (queryVersion) filterRepositoryVersionFragment else ""}
        ORDER BY timestamp, stream_id, transaction_seq_nr
        LIMIT ?
        """.trimIndent()

    private val filterStreamVersionFragment = """
        AND timestamp >= ?
        AND (timestamp > ? OR transaction_seq_nr > ?)
        """.trimIndent()

    private fun makeReadStreamEventsQuery(queryVersion: Boolean) = """
        $readEventDataPrelude
        WHERE stream_hash = ?
        AND namespace = ?
        AND repository = ?
        AND stream_id = ?
        ${if (queryVersion) filterStreamVersionFragment else ""}
        ORDER BY timestamp, transaction_seq_nr
        LIMIT ?
        """.trimIndent()

    private val writeEventDataStatement = """
        INSERT INTO $eventsTableName (
            stream_hash, namespace, repository, stream_id, timestamp,
            transaction_seq_nr, event_id, event_type, data, metadata
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

    companion object {
        // Some DB engines like MySql still use a signed int internally to represent timestamps, use this as the
        // highest safe value to use for a placeholder timestamp. The sentinel value is always created when the stream
        // is and marks the end of the stream.
        const val SENTINEL_TIMESTAMP_UNIX = Int.MAX_VALUE.toLong()
        val SENTINEL_TIMESTAMP: Instant = Instant.ofEpochSecond(SENTINEL_TIMESTAMP_UNIX)
        val SENTINEL_VERSION = DbstoreStreamVersion(SENTINEL_TIMESTAMP, 0)

        private fun <T> ResultSet.readRowsWith(readEventRow: (ResultSet) -> T): List<T> {
            val results = mutableListOf<T>()

            while (next()) {
                val processedRow = readEventRow(this)
                results.add(processedRow)
            }

            return results
        }
    }
}
