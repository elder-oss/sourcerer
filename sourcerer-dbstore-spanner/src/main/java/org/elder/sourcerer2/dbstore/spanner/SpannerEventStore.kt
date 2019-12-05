package org.elder.sourcerer2.dbstore.spanner

import com.google.cloud.spanner.DatabaseClient
import com.google.cloud.spanner.KeyRange
import com.google.cloud.spanner.KeySet
import com.google.cloud.spanner.Statement
import org.elder.sourcerer2.StreamId
import org.elder.sourcerer2.dbstore.DbstoreEventRow
import org.elder.sourcerer2.dbstore.DbstoreEventStore
import org.elder.sourcerer2.dbstore.DbstoreStreamVersion

class SpannerEventStore(
        private val client: DatabaseClient,
        private val eventsTableName: String
) : DbstoreEventStore {
    override fun readStreamEvents(
            streamId: StreamId,
            category: String,
            fromVersion: DbstoreStreamVersion?,
            maxEvents: Int
    ): List<DbstoreEventRow> {
        client.readOnlyTransaction().use {
            val foo = Statement.newBuilder().append().build()
            foo.parameters.
            it.executeQuery(

            )
            it.analyzeQuery()
            it.read(
                    eventsTableName,
                    KeySet
                            .newBuilder()
                            .addRange(KeyRan)

            )
                .readTimestamp
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            KeySet.newBuilder()
                    .addRange(KeyRange.newBuilder().setStart())

            it.readTimestamp
        }
    }
}
