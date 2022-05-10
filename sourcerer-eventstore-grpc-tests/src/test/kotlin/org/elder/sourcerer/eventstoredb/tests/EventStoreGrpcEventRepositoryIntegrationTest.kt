package org.elder.sourcerer.eventstoredb.tests

import com.eventstore.dbclient.Endpoint
import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBClientSettings
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.elder.sourcerer.EventRepositoryFactory
import org.elder.sourcerer.eventstore.tests.EventStorEventRepositoryIntegrationTestBase
import org.elder.sourcerer.eventstore.tests.EventstoreInstance
import org.elder.sourcerer.eventstoredb.EventStoreGrpcEventRepositoryFactory

class EventStoreGrpcEventRepositoryIntegrationTest : EventStorEventRepositoryIntegrationTestBase() {
    override fun createRepositoryFactory(db: EventstoreInstance): EventRepositoryFactory {
        val settings = EventStoreDBClientSettings.builder()
                .addHost(Endpoint("127.0.0.1", db.httpPort))
                .defaultCredentials("admin", "changeit")
                .throwOnAppendFailure(true)
                .tls(false)
                .keepAliveInterval(10000)
                .buildConnectionSettings()

        val eventstoreDbClient = EventStoreDBClient.create(settings)
        val objectMapper = ObjectMapper()
        objectMapper.registerKotlinModule()

        return EventStoreGrpcEventRepositoryFactory(
                eventstoreDbClient,
                objectMapper,
                "integrationtest")
    }
}