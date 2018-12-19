package org.elder.sourcerer2.dbstore.jdbc

import org.elder.sourcerer2.EventId
import org.elder.sourcerer2.StreamId
import org.elder.sourcerer2.dbstore.DbstoreEventData
import org.elder.sourcerer2.dbstore.DbstoreEventRow
import org.elder.sourcerer2.dbstore.DbstoreRepositoryInfo
import org.elder.sourcerer2.dbstore.DbstoreStreamVersion
import org.elder.sourcerer2.dbstore.DbstoreUnexpectedVersionException
import org.elder.sourcerer2.dbstore.FoundWhenNotExpectedException
import org.elder.sourcerer2.dbstore.FoundWithDifferentVersionException
import org.elder.sourcerer2.dbstore.NotFoundWhenExpectedException
import org.h2.jdbcx.JdbcDataSource
import org.junit.Assert
import org.junit.Test
import java.time.Instant
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
    fun readingDataFromStartWorks() {
        val eventStore = getCleanEventStore()
        val events = listOf(
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data1", "md1"),
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data2", "md3"),
                DbstoreEventData(EventId.newUniqueId(), "testEvent2", "data3", "md3")
        )

        eventStore.appendStreamEvents(
                testRepositoryInfo,
                testStreamId,
                null,
                null,
                events
        )

        val result = eventStore.readStreamEvents(testRepositoryInfo, testStreamId)
        Assert.assertEquals("Right number of rows returned", 4, result.size)
        Assert.assertTrue("Ends with end of stream", result.last() is DbstoreEventRow.EndOfStream)
        Assert.assertEquals(
                "Right event inserted first",
                events[0].eventId,
                (result[0] as DbstoreEventRow.Event).eventData.eventId
        )
    }

    @Test
    fun readingDataFromVersionStartWorks() {
        val eventStore = getCleanEventStore()
        val events = listOf(
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data1", "md1"),
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data2", "md3"),
                DbstoreEventData(EventId.newUniqueId(), "testEvent2", "data3", "md3")
        )
        val events2 = listOf(
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data1", "md1")
        )

        val version = eventStore.appendStreamEvents(
                testRepositoryInfo,
                testStreamId,
                null,
                null,
                events
        )
        eventStore.appendStreamEvents(
                testRepositoryInfo,
                testStreamId,
                null,
                null,
                events2
        )

        val result = eventStore.readStreamEvents(testRepositoryInfo, testStreamId, version)
        Assert.assertEquals("Right number of rows returned", 2, result.size)
        Assert.assertTrue("Ends with end of stream", result.last() is DbstoreEventRow.EndOfStream)
        Assert.assertEquals(
                "Right event read",
                events2[0].eventId,
                (result[0] as DbstoreEventRow.Event).eventData.eventId
        )
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


        val repoResult = eventStore.readRepositoryEvents(testRepositoryInfo)
        Assert.assertEquals("Expecting repository to contain 4 rows", 4, repoResult.size)

        val streamResult = eventStore.readStreamEvents(testRepositoryInfo, testStreamId)
        Assert.assertEquals("Expecting stream to contain 4 rows", 4, streamResult.size)
    }

    @Test(expected = NotFoundWhenExpectedException::class)
    fun appendFailsOnMissingWhenAssertingExits() {
        val eventStore = getCleanEventStore()
        val events = listOf(
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data1", "md1"),
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data2", "md3"),
                DbstoreEventData(EventId.newUniqueId(), "testEvent2", "data3", "md3")
        )

        eventStore.appendStreamEvents(
                testRepositoryInfo,
                testStreamId,
                true,
                null,
                events
        )
    }

    @Test(expected = FoundWhenNotExpectedException::class)
    fun appendFailsOnExistingWhenAssertingNew() {
        val eventStore = getCleanEventStore()
        val events = listOf(
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data1", "md1"),
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data2", "md3"),
                DbstoreEventData(EventId.newUniqueId(), "testEvent2", "data3", "md3")
        )
        val events2 = listOf(
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data1", "md1")
        )

        // Insert some data
        eventStore.appendStreamEvents(
                testRepositoryInfo,
                testStreamId,
                null,
                null,
                events
        )

        // Try to add more, asserting new stream
        eventStore.appendStreamEvents(
                testRepositoryInfo,
                testStreamId,
                false,
                null,
                events2
        )
    }

    @Test(expected = FoundWithDifferentVersionException::class)
    fun appendFailsWhenExistsButDifferentVersion() {
        val eventStore = getCleanEventStore()
        val events = listOf(
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data1", "md1"),
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data2", "md3"),
                DbstoreEventData(EventId.newUniqueId(), "testEvent2", "data3", "md3")
        )
        val events2 = listOf(
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data1", "md1")
        )

        // Insert some data
        eventStore.appendStreamEvents(
                testRepositoryInfo,
                testStreamId,
                null,
                null,
                events
        )

        // Try to add more, asserting new stream
        eventStore.appendStreamEvents(
                testRepositoryInfo,
                testStreamId,
                true,
                DbstoreStreamVersion(
                        Instant.now().minusSeconds(60 * 60 * 24),
                        0
                ),
                events2
        )
    }

    @Test
    fun appendWorksWheExpectedVersionMatches() {
        val eventStore = getCleanEventStore()
        val events = listOf(
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data1", "md1"),
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data2", "md3"),
                DbstoreEventData(EventId.newUniqueId(), "testEvent2", "data3", "md3")
        )
        val events2 = listOf(
                DbstoreEventData(EventId.newUniqueId(), "testEvent", "data1", "md1")
        )

        // Insert some data
        val newVersion = eventStore.appendStreamEvents(
                testRepositoryInfo,
                testStreamId,
                null,
                null,
                events
        )

        // Try to add more, asserting new stream
        eventStore.appendStreamEvents(
                testRepositoryInfo,
                testStreamId,
                true,
                newVersion,
                events2
        )
    }
}
