package org.elder.sourcerer2.dbstore

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
import java.sql.ResultSet

internal class DbstoreEventRepository<T>(
        private val eventStore: DbstoreEventStore,
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
        val jdbcVersion = version?.toDbstoreStreamVersion()
        val eventRows = eventStore.readStreamEvents(streamId, categoryName, jdbcVersion, maxEvents)
        val events = mutableListOf<EventRecord<T>>()
        var lastVersion: DbstoreStreamVersion? = null

        eventRows.forEach {
            when (it) {
                is DbstoreEventRow.Event -> {
                    events.add(parseEvent(it))
                    lastVersion = DbstoreStreamVersion(it.eventData.timestamp, it.eventData.transactionSeqNr)
                }
                is DbstoreEventRow.EndOfStream -> {
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

