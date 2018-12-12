package org.elder.sourcerer2.dbstore.spanner

import org.elder.sourcerer2.StreamId
import org.elder.sourcerer2.dbstore.DbstoreEventRow
import org.elder.sourcerer2.dbstore.DbstoreEventStore
import org.elder.sourcerer2.dbstore.DbstoreStreamVersion

class SpannerEventStore : DbstoreEventStore {
    override fun readStreamEvents(
            streamId: StreamId,
            category: String,
            fromVersion: DbstoreStreamVersion?,
            maxEvents: Int
    ): List<DbstoreEventRow> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}