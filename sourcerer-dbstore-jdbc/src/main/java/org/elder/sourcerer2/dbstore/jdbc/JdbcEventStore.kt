package org.elder.sourcerer2.dbstore.jdbc

import org.elder.sourcerer2.EventId
import org.elder.sourcerer2.StreamId
import org.elder.sourcerer2.dbstore.DbstoreEventRecord
import org.elder.sourcerer2.dbstore.DbstoreEventRow
import org.elder.sourcerer2.dbstore.DbstoreEventStore
import org.elder.sourcerer2.dbstore.DbstoreStreamVersion
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

internal class JdbcEventStore(
        private val dataSource: DataSource,
        private val eventsTableName: String,
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
            connection.commit()
            val readStatement = connection.createReadStreamStatement(
                    streamId,
                    category,
                    effectiveMaxEvents,
                    fromVersion)

            readStatement.executeQuery().use {
                it.readEventDataResults()
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

    private fun getMaxReadBatchSize(requestedBatchSize: Int): Int {
        return minOf(requestedBatchSize, maxReadBatchSize)
    }

    private fun ResultSet.readEventDataResults(): List<DbstoreEventRow> {
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

    private val makeReadLastStreamEventQuery = """
        SELECT timestamp, batch_sequence_nr
        FROM $eventsTableName
        WHERE stream_id = ?
        AND category = ?
        ORDER BY timestamp DESC, batch_sequence_nr DESC
        LIMIT 1
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

    companion object {
        // Some DB engines like MySql still use a signed int internally to represent timestamps, use this as the
        // highest safe value to use for a placeholder timestamp. The sentinel value is always created when the stream
        // is and marks the end of the stream.
        val SENTINEL_TIMESTAMP: Instant = Instant.ofEpochSecond(Int.MAX_VALUE.toLong())

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
