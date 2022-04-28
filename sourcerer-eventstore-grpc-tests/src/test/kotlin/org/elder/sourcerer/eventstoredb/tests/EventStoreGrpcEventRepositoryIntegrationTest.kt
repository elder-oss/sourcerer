package org.elder.sourcerer.eventstoredb.tests

import com.eventstore.dbclient.Endpoint
import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBClientSettings
import com.fasterxml.jackson.databind.ObjectMapper
import org.elder.sourcerer.EventData
import org.elder.sourcerer.EventRepositoryFactory
import org.elder.sourcerer.ExpectedVersion
import org.elder.sourcerer.eventstoredb.EventStoreGrpcEventRepositoryFactory
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert
import org.junit.Test
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.util.*

class EventStoreGrpcEventRepositoryIntegrationTest {

    @Test
    fun canReadWriteSingleEvent() {
        EventstoreInstance().use { eventstoreDb ->
            eventstoreDb.ensureStarted()
            val repoFactory = createRepositoryFactory(eventstoreDb.port)
            val testRepo = repoFactory.getEventRepository(TestEventType::class.java)
            val streamId = UUID.randomUUID().toString()

            val event = TestEventType("one")
            testRepo.append(streamId, listOf(eventData(event)), ExpectedVersion.any())
            val events = testRepo.read(streamId)

            Assert.assertThat(events.events.size, equalTo(1))
            Assert.assertThat(events.events[0].event, equalTo(event))
        }
    }

    private fun <T> eventData(event: T): EventData<T> {
        return EventData(
                "eventType",
                UUID.randomUUID(),
                mapOf(),
                event
        )
    }

    private fun createRepositoryFactory(port: Int): EventRepositoryFactory {
        val settings = EventStoreDBClientSettings.builder()
                .addHost(Endpoint("127.0.0.1", port))
                .defaultCredentials("admin", "changeit")
                .throwOnAppendFailure(true)
                .tls(false)
                .keepAliveInterval(10000)
                .buildConnectionSettings()

        val eventstoreDbClient = EventStoreDBClient.create(settings)
        val objectMapper = ObjectMapper()
        objectMapper.registerKotlinModule()

        return EventStoreGrpcEventRepositoryFactory(eventstoreDbClient, objectMapper, "testtest")
    }
}