package org.elder.sourcerer2.jdbc

import com.google.common.collect.ImmutableList
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
import java.sql.PreparedStatement
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
    ): StreamReadResult<T>? {
        val effectiveMaxEvents = getMaxReadBatchSize(maxEvents)
        return withConnection(true) { connection ->
            val readStatement = connection.createReadStreamStatement(
                    streamId,
                    categoryName,
                    effectiveMaxEvents,
                    version)

            var result = readStatement.executeQuery().use {
                processReadResults(it, effectiveMaxEvents)
            }

            if (result == null) {
                // No results newer than what we asked for, or stream does not exist, figure out
                // which it is.
                if (version == null) {
                    // No version was provided (we're reading from the start), and we had no
                    // results. This can only mean that the stream did not exist at the time
                    null
                } else {
                    // Slow path - we have no events matching the query, this could either be from
                    // the stream not existing at all, or us having to gone off the end - check again
                    // if we do have any events
                    connection.prepareStatement(readLastStreamEventQuery).use {
                        it.setString(0, streamId.identifier)
                        it.setString(1, categoryName)
                        it.executeQuery().use { resultSet ->
                            if (resultSet.next()) {
                                // We have events in the stream, but none matching the given query.
                                // This can happen if the previous call used up all of the events,
                                // in which case the event returned here should match the provided
                                // version exactly.


                                StreamReadResult(
                                        events = ImmutableList.of(),
                                        version = version,
                                        isEndOfStream = true
                                )
                            } else {
                                // We have no events, meaning there was no stream in the first
                                // place, just tell the world - nothing to see here
                                null
                            }
                        }
                    }
                }

            }
        }
    }

    private fun processReadResults(resultSet: ResultSet, batchSize: Int): StreamReadResult<T>? {
        val events = mutableListOf<EventRecord<T>>()
        var lastVersion: StreamVersion? = null
        var count = 0

        while (resultSet.next()) {
            val eventRecords = readResult(resultSet)
            lastVersion = eventRecords.streamVersion
            events.add(eventRecords)
            count++
            resultSet.next()
        }

        return if (events.isNotEmpty()) {
            // We return end of stream at to points in time, this one if we have fewer events
            // than the query asked for (happy path) and in the fallback logic if a query returned
            // no results but the stream does exist.
            StreamReadResult(
                    events = ImmutableList.copyOf(events),
                    version = lastVersion!!,
                    isEndOfStream = count < batchSize)
        } else {
            // Nothing to see here, no results. This is either because there are no more events
            // past the version specified, or because the stream doesn't exist at all, leave it to
            // the main function to decide which is which
            null
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

    private fun Connection.createReadStreamStatement(
            streamId: StreamId,
            categoryName: String,
            effectiveMaxEvents: Int,
            version: StreamVersion?
    ): PreparedStatement {
        return when (version) {
            null -> {
                val statement = prepareStatement(readStreamEventsQuery)
                statement.setString(0, streamId.identifier)
                statement.setString(1, categoryName)
                statement.setInt(2, effectiveMaxEvents)
                statement
            }
            else -> {
                val jdbcStreamVersion = version.toJdbcStreamVersion()
                val statement = prepareStatement(readStreamEventsFromQuery)
                statement.setString(0, streamId.identifier)
                statement.setString(1, categoryName)
                statement.setTimestamp(2, Timestamp.from(jdbcStreamVersion.timestamp))
                statement.setTimestamp(3, Timestamp.from(jdbcStreamVersion.timestamp))
                statement.setInt(4, jdbcStreamVersion.batchSequenceNr)
                statement.setInt(5, effectiveMaxEvents)
                statement
            }
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

