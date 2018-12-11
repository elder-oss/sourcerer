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
import java.time.Instant
import javax.sql.DataSource
import kotlin.math.max

internal class JdbcEventRepository<T>(
        private val eventStore: JdbcEventStore,
        private val eventType: Class<T>,
        private val categoryName: String
) : EventRepository<T> {
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
        val jdbcVersion = version?.toJdbcStreamVersion()
        val eventRows = eventStore.readStreamEventsFromStart(streamId, categoryName, jdbcVersion, maxEvents)
        val events = mutableListOf<EventRecord<T>>()
        var lastVersion: JdbcStreamVersion? = null

        eventRows.forEach {
            when (it) {
                is JdbcEventRow.Event -> {
                    events.add(parseEvent(it))
                    lastVersion = JdbcStreamVersion(it.eventData.timestamp, it.eventData.transactionSeqNr)
                }
                is JdbcEventRow.EndOfStream -> {
                    return StreamReadResult(
                            events = ImmutableList.copyOf(events),
                            version = lastVersion?.toStreamVersion() ?: version,
                            isEndOfStream = true
                    )
                }
            }
        }
    }

    private fun readResult(resultSet: ResultSet): EventRecord<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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



}

