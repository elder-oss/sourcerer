package org.elder.sourcerer2.dbstore.jdbc

import org.elder.sourcerer2.EventId
import org.elder.sourcerer2.StreamId
import org.elder.sourcerer2.dbstore.DbstoreEventData
import org.elder.sourcerer2.dbstore.DbstoreRepositoryInfo
import org.h2.jdbcx.JdbcDataSource
import org.junit.Assert
import org.junit.Test
import javax.sql.DataSource

class JdbcEventStoreTest {
    data class DummyEvent(val nothing: String)

    private val testStreamId = StreamId.ofString("teststreamid")
    private val testStreamId2 = StreamId.ofString("teststreamid2")
    private val testRepositoryInfo = DbstoreRepositoryInfo(
            DummyEvent::class.java,
            "default",
            "testItems",
            8,
            null
    )

    private fun getCleanH2DataSource(): DataSource {
        val dataSource = JdbcDataSource()
        dataSource.setURL("jdbc:h2:mem:estest;DB_CLOSE_DELAY=-1;MVCC=true")
        dataSource.user = "sa"
        dataSource.connection.use { con ->
            val statement = con.createStatement()
            statement.execute(Sql.CREATE_DB_SCHEMA)
        }

        return dataSource
    }

    private fun getCleanEventStore(): JdbcEventStore {
        val dataSource = getCleanH2DataSource()
        return JdbcEventStore(dataSource, "events")
    }

    @Test
    fun readingMissingStreamReturnsEmpty() {
        val eventStore = getCleanEventStore()
        val result = eventStore.readStreamEvents(testRepositoryInfo, testStreamId)
        Assert.assertTrue("Results should be an empty list", result.isEmpty())
    }

    @Test
    fun readingEmptyRepositoryReturnsEmpty() {
        val eventStore = getCleanEventStore()
        val result = eventStore.readRepositoryEvents(testRepositoryInfo)
        Assert.assertTrue("Results should be an empty list", result.isEmpty())
    }

    @Test
    fun appendingToNewStreamCreates() {
        val eventStore = getCleanEventStore()
        val events = listOf(
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data1", "md1"),
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data2", "md3"),
                DbstoreEventData(EventId.newUniqueId(), "testEvent2", "data3", "md3")
        )

        val result = eventStore.appendStreamEvents(
                testRepositoryInfo,
                testStreamId,
                null,
                null,
                events
        )

        Assert.assertEquals(
                "Transaction seq nr should match inserted events",
                2, result.transactionSequenceNr)

        val result2 = eventStore.appendStreamEvents(
                testRepositoryInfo,
                testStreamId2,
                null,
                null,
                events
        )

        val repoResult = eventStore.readRepositoryEvents(testRepositoryInfo)
        val streamResult = eventStore.readStreamEvents(testRepositoryInfo, testStreamId)
    }
}
