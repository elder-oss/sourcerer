package org.elder.sourcerer2.dbstore

/**
 * Typed representation of a row as stored in the underlying database, either representing an event, or the sentinel
 * value of "end of stream"
 */
sealed class DbstoreEventRow {
    data class Event(val eventData: DbstoreEventRecord) : DbstoreEventRow()
    object EndOfStream : DbstoreEventRow()
}
