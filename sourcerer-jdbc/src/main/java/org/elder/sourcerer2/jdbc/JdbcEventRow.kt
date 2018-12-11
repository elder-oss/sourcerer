package org.elder.sourcerer2.jdbc

import org.elder.sourcerer2.StreamId

/**
 * Typed representation of a row as stored in the underlying database, either representing an event, or the sentinel
 * value of "end of stream"
 */
sealed class JdbcEventRow {
    data class Event(val eventData: JdbcEventRecord) : JdbcEventRow()
    data class EndOfStream(val streamId: StreamId, val category: String) : JdbcEventRow()
}
