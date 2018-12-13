package org.elder.sourcerer2.dbstore

import org.elder.sourcerer2.StreamId

/**
 * Typed representation of a row as stored in the underlying database, either representing an event, or the sentinel
 * value of "end of stream"
 */
sealed class DbstoreEventRow {
    data class Event(val eventData: DbstoreEventRecord) : DbstoreEventRow()
    data class EndOfStream(val streamId: StreamId, val category: String, val shard: Int) : DbstoreEventRow()

    fun getShard(): Int {
        return when (this) {
            is Event -> eventData.shard
            is EndOfStream -> shard
        }
    }
}
