package org.elder.sourcerer.esjc.tests

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.msemys.esjc.EventStoreBuilder
import org.elder.sourcerer.EventRepositoryFactory
import org.elder.sourcerer.esjc.EventStoreEsjcEventRepositoryFactory
import org.elder.sourcerer.eventstore.tests.EventStoreEventRepositoryIntegrationTestBase
import org.elder.sourcerer.eventstore.tests.EventstoreInstance

class EventStoreEsjcEventRepositoryIntegrationTest
    : EventStoreEventRepositoryIntegrationTestBase(enableLegacyTcpInterface = true) {
    override fun createRepositoryFactory(db: EventstoreInstance): EventRepositoryFactory {
        val eventStore = EventStoreBuilder
                .newBuilder()
                .userCredentials("admin", "changeit")
                .requireMaster(false)
                .failOnNoServerResponse(true)
                .singleNodeAddress("127.0.0.1", db.tcpPort)
                .build()

        eventStore.connect()

        val objectMapper = ObjectMapper()
        objectMapper.registerKotlinModule()

        return EventStoreEsjcEventRepositoryFactory(
                eventStore,
                objectMapper,
                "integrationtests")
    }
}