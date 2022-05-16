package org.elder.sourcerer.eventstoredb

import com.eventstore.dbclient.Endpoint
import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBClientSettings
import com.eventstore.dbclient.NodePreference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

fun createGrpcRepositoryFactory(sessionId: String): EventStoreGrpcEventRepositoryFactory {
    val settings = EventStoreDBClientSettings.builder()
            .addHost(Endpoint("127.0.0.1", 2113))
            .addHost(Endpoint("127.0.0.1", 2114))
            // .addHost(Endpoint("127.0.0.1", 2115))
            .defaultCredentials("admin", "changeit")
            .nodePreference(NodePreference.FOLLOWER)
            .throwOnAppendFailure(true)
            .tls(false)
            .keepAliveInterval(10000)
            .buildConnectionSettings()

    val eventstoreDbClient = EventStoreDBClient.create(settings)
    val objectMapper = ObjectMapper()
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    objectMapper.registerKotlinModule()

    return EventStoreGrpcEventRepositoryFactory(
            eventstoreDbClient,
            objectMapper,
            "tests_$sessionId")
}
