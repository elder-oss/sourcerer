package org.elder.sourcerer2.dbstore.jdbc

import org.elder.sourcerer2.EventId
import org.elder.sourcerer2.StreamId
import org.elder.sourcerer2.dbstore.DbstoreEventData
import org.elder.sourcerer2.dbstore.DbstoreEventRecord
import org.elder.sourcerer2.dbstore.DbstoreEventRow
import org.elder.sourcerer2.dbstore.DbstoreEventStore
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
internal class JdbcEventStore(
        private val dataSource: DataSource,
        private val eventsTableName: String,
        private val maxShards: Int = 8,
        private val maxReadBatchSize: Int = 2048
) : DbstoreEventStore {
    override fun readStreamEvents(
            streamId: StreamId,
            category: String,
            fromVersion: DbstoreStreamVersion?,
            maxEvents: Int
    ): List<DbstoreEventRow> {
        val effectiveMaxEvents = getMaxReadBatchSize(maxEvents)
        return withConnection(true) { connection ->
            connection.readStreamEvents(streamId, category, effectiveMaxEvents, fromVersion)
        }
    }

    override fun appendStreamEvents(
            streamId: StreamId,
            category: String,
            expectExisting: Boolean?,
            expectVersion: DbstoreStreamVersion?,
            events: List<DbstoreEventData>
    ) {
        // If we have an expected version, go look for the event after that point (or the sentinel if not found),
        // if we don't, then just look for the sentinel directly.
        return withConnection(true) { connection ->
            val fromVersion = expectVersion ?: SENTINEL_VERSION
            val rowsFromExpectedEnd = connection.readStreamEvents(streamId, category, 1, fromVersion)
            expectExisting?.let { assertExistenceStatus(rowsFromExpectedEnd, it) }
            expectVersion?.let { assertExpectedVersion(rowsFromExpectedEnd, events[0].eventId) }

            val commitTimestamp = connection.claimCommitTimestamp()
            connection.persistEvents(commitTimestamp, events)
            connection.commit()
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
                    is DbstoreEventRow.Event -> row.eventData.getVersion()
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
                    throw FoundWithDifferentVersionException(foundRow.eventData.getVersion())
                }
            }
        }
    }

    private fun Connection.createReadStreamStatement(
            streamId: StreamId,
            categoryName: String,
            effectiveMaxEvents: Int,
            fromVersion: DbstoreStreamVersion?
    ): PreparedStatement {
        return when (fromVersion) {
            null -> {
                val statement = prepareStatement(readStreamEventsFromBeginningQuery)
                statement.setString(0, streamId.identifier)
                statement.setString(1, categoryName)
                statement.setInt(2, effectiveMaxEvents)
                statement
            }
            else -> {
                val statement = prepareStatement(readStreamEventsFromVersionQuery)
                statement.setString(0, streamId.identifier)
                statement.setString(1, categoryName)
                statement.setTimestamp(2, Timestamp.from(fromVersion.timestamp))
                statement.setTimestamp(3, Timestamp.from(fromVersion.timestamp))
                statement.setInt(4, fromVersion.transactionSequenceNr)
                statement.setInt(5, effectiveMaxEvents)
                statement
            }
        }
    }

    private fun <T> withConnection(readOnly: Boolean, dbOperation: (Connection) -> T): T {
        return dataSource.connection.use {
            it.autoCommit = false
            // TODO: Do we need this also for read queries? Probably not
            it.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            if (readOnly) {
                it.isReadOnly = true
            }
            dbOperation(it)
        }
    }

    private fun Connection.readStreamEvents(
            streamId: StreamId,
            category: String,
            effectiveMaxEvents: Int,
            fromVersion: DbstoreStreamVersion?
    ): List<DbstoreEventRow> {
        val readStatement = createReadStreamStatement(
                streamId,
                category,
                effectiveMaxEvents,
                fromVersion)

        return readStatement.executeQuery().use {
            it.readEventRowResults()
        }
    }

    private fun getMaxReadBatchSize(requestedBatchSize: Int): Int {
        return minOf(requestedBatchSize, maxReadBatchSize)
    }

    private fun ResultSet.readEventRowResults(): List<DbstoreEventRow> {
        return this.readRowsWith(::readEventRow)
    }

    private fun readEventRow(row: ResultSet): DbstoreEventRow {
        val streamId = StreamId.ofString(row.getString(0))
        val category = row.getString(1)
        val shard = row.getInt(2)
        val timestamp = row.getTimestamp(3).toInstant()

        return when (timestamp) {
            SENTINEL_TIMESTAMP ->
                // This is the end of stream marker, only extract the bits relevant to it
                DbstoreEventRow.EndOfStream(
                        streamId = streamId,
                        category = category
                )
            else -> {
                val transactionSeqNr = row.getInt(4)
                val eventId = EventId.ofUuid(UUID.fromString(row.getString(5)))
                val eventType = row.getString(6)
                val data = row.getString(7)
                val metadata = row.getString(8)
                val event = DbstoreEventRecord(
                        streamId = streamId,
                        category = category,
                        shard = shard,
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

    private fun Connection.claimCommitTimestamp(): Timestamp {
        // NOTE: This is a locking operation and a bottleneck for event writes. A more intrinsic way to accomplish this
        // would be to use an auto increment column, however, we want this to work as closely as possible to the Spanner
        // backend and expect this to be used for testing/development - not production, so we are OK with paying the
        // price of this. This also requires transactions to use SERIALIZABLE to ensure that timestamps are indeed
        // unique.
        val resultSet = prepareStatement(getCommitTimestampQuery).executeQuery()
        resultSet.next()
        return resultSet.getTimestamp(0)
    }

    private fun Connection.persistEvents(commitTimestamp: Timestamp, events: List<DbstoreEventData>) {
        events.forEachIndexed { idx, eventData ->
            val statement = prepareStatement(writeEventDataStatement)
            statement.setString(0, eventData.streamId.identifier)
            statement.setString(1, eventData.category)
            statement.setString(2, eventData.getShard(maxShards))
            statement.setTimestamp(3, commitTimestamp)
            statement.setInt(4, idx)
            statement.setString(5, eventData.eventId.id.toString())
            statement.setString(6, eventData.eventType)
            statement.setString(7, eventData.data)
            statement.setString(8, eventData.metadata)
            statement.execute()
        }
    }

    private val getCommitTimestampQuery = """
        SELECT GREATER(UTC_TIMESTAMP(6), TIMESTAMPADD(MICROSECOND, 1, MAX(timestamp))) AS commit_timestamp
        FROM $eventsTableName
        """.trimIndent()

    private val readEventDataPrelude = """
        SELECT stream_id, category, shard, timestamp, transaction_seq_nr, event_id, event_type, data, metadata
        FROM $eventsTableName
        """.trimIndent()

    private val readStreamEventsPostlude = """
        ORDER BY timestamp, batch_sequence_nr
        LIMIT ?
        """.trimIndent()

    private val readStreamEventsFromBeginningQuery = """
        $readEventDataPrelude
        WHERE stream_id = ?
        AND category = ?
        $readStreamEventsPostlude
        """.trimIndent()

    private val readStreamEventsFromVersionQuery = """
        $readEventDataPrelude
        WHERE stream_id = ?
        AND category = ?
        AND timestamp >= ?
        AND (timestamp > ? OR batch_sequence_nr > ?)
        $readStreamEventsPostlude
    """.trimIndent()

    private val writeEventDataStatement = """
        INSERT INTO $eventsTableName
            (stream_id, category, shard, timestamp, transaction_seq_nr, event_id, event_type, data, metadata)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

    companion object {
        // Some DB engines like MySql still use a signed int internally to represent timestamps, use this as the
        // highest safe value to use for a placeholder timestamp. The sentinel value is always created when the stream
        // is and marks the end of the stream.
        val SENTINEL_TIMESTAMP: Instant = Instant.ofEpochSecond(Int.MAX_VALUE.toLong())
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

