package org.elder.sourcerer2.jdbc

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
import org.reactivestreams.Publisher
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import javax.sql.DataSource

class JdbcEventRepository<T>(
        private val eventType: Class<T>,
        private val dataSource: DataSource,
        private val categoryName: String,
        private val eventsTableName: String,
        private val maxReadBatchSize: Int = 2048
) : EventRepository<T> {
    private val readLastStreamEventQuery = makeReadLastStreamEventQuery()
    private val readStreamEventsQuery = makeReadStreamEventsQuery()
    private val readStreamEventsFromQuery = makeReadStreamEventsFromQuery()

    override fun getEventType(): Class<T> {
        return eventType
    }

    override fun readAll(version: RepositoryVersion?, maxEvents: Int): RepositoryReadResult<T> {
        TODO()
    }

    override fun read(
            streamId: StreamId,
            version: StreamVersion?,
            maxEvents: Int
    ): StreamReadResult<T> {
        val effectiveMaxEvents = getMaxReadBatchSize(maxEvents)
        return withConnection(true) {
            val readStatement = when (version) {
                null -> {
                    val statement = it.prepareStatement(readStreamEventsQuery)
                    statement.setString(0, streamId.identifier)
                    statement.setString(1, categoryName)
                    statement.setInt(2, effectiveMaxEvents)
                    statement
                }
                else -> {
                    val jdbcStreamVersion = version.toJdbcStreamVersion()
                    val statement = it.prepareStatement(readStreamEventsFromQuery)
                    statement.setString(0, streamId.identifier)
                    statement.setString(1, categoryName)
                    statement.setTimestamp(2, Timestamp.from(jdbcStreamVersion.timestamp))
                    statement.setTimestamp(3, Timestamp.from(jdbcStreamVersion.timestamp))
                    statement.setInt(4, jdbcStreamVersion.batchSequenceNr)
                    statement.setInt(5, effectiveMaxEvents)
                    statement
                }
            }

            val events = mutableListOf<EventRecord<T>>()
            var lastVersion: StreamVersion? = null

            readStatement.use { statement ->
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val eventRecords = readResult(resultSet)
                        lastVersion = eventRecords.streamVersion
                        events.add(eventRecords)
                        resultSet.next()
                    }

                }
            }

            if (lastVersion == null) {
                // Slow path - we have no events matching the query, this could either be from
                // the stream not existing at all, or us having to gone off the end - check again
                // if we do have any events
                val statement = it.prepareStatement(readLastStreamEventQuery)
                statement.setString(0, streamId.identifier)
                statement.setString(1, categoryName)

            }
        }
    }

    private fun readResult(resultSet: ResultSet): EventRecord<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun getMaxReadBatchSize(requestedBatchSize: Int): Int {
        return minOf(requestedBatchSize, maxReadBatchSize)
    }

    override fun readFirst(streamId: StreamId): EventRecord<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun readLast(streamId: StreamId): EventRecord<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getCurrentVersion(): RepositoryVersion {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getCurrentVersion(streamId: StreamId): StreamVersion {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun append(streamId: StreamId?, events: MutableList<EventData<T>>?, version: ExpectedVersion?): StreamVersion {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getStreamPublisher(streamId: StreamId, fromVersion: StreamVersion?): Publisher<EventSubscriptionUpdate<T>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getPublisher(fromVersion: RepositoryVersion?): Publisher<EventSubscriptionUpdate<T>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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
    private fun makeReadLastStreamEventQuery(): String {
        return """
                SELECT timestamp, batch_sequence_nr
                FROM $eventsTableName
                WHERE stream_id = ?
                AND category = ?
                ORDER BY timestamp DESC, batch_sequence_nr DESC
                LIMIT 1
            """.trimIndent()
    }

    private fun makeReadStreamEventsQuery(): String {
        return """
                SELECT timestamp, batch_sequence_nr, event_type, event_data, metadata
                FROM $eventsTableName
                WHERE stream_id = ?
                AND category = ?
                ORDER BY timestamp, batch_sequence_nr
                LIMIT ?
            """.trimIndent()
    }

    private fun makeReadStreamEventsFromQuery(): String {
        return """
                SELECT timestamp, batch_sequence_nr, event_type, event_data, metadata
                FROM $eventsTableName
                WHERE stream_id = ?
                AND category = ?
                AND timestamp >= ?
                AND (timestamp > ? OR batch_sequence_nr > ?)
                ORDER BY timestamp, batch_sequence_nr
                LIMIT ?
            """.trimIndent()
    }
}
