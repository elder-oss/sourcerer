package org.elder.sourcerer2.dbstore

import org.elder.sourcerer2.StreamId

interface DbstoreEventStore {
    fun readStreamEvents(
            streamId: StreamId,
            category: String,
            fromVersion: DbstoreStreamVersion? = null,
            maxEvents: Int = Int.MAX_VALUE
    ): List<DbstoreEventRow>
}
