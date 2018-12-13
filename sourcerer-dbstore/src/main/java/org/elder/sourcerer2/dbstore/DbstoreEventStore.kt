package org.elder.sourcerer2.dbstore

import org.elder.sourcerer2.StreamId


interface DbstoreEventStore {
    fun readStreamEvents(
            streamId: StreamId,
            category: String,
            fromVersion: DbstoreStreamVersion? = null,
            maxEvents: Int = Int.MAX_VALUE
    ): List<DbstoreEventRow>

    /**
     * @throws DbstoreUnexpectedVersionException if the current version of a stream does not match
     * the expected requirements.
     */
    fun appendStreamEvents(
            streamId: StreamId,
            category: String,
            expectExisting: Boolean?,
            expectVersion: DbstoreStreamVersion?,
            events: List<DbstoreEventData>
    )
}
