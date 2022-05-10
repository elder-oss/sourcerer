package org.elder.sourcerer.esjc.tests

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.msemys.esjc.EventStoreBuilder
import org.elder.sourcerer.esjc.EventStoreEsjcEventRepositoryFactory

fun createEsjcRepositoryFactory(sessionId: String): EventStoreEsjcEventRepositoryFactory {
    val eventStore = EventStoreBuilder
            .newBuilder()
            .userCredentials("admin", "changeit")
            .requireMaster(false)
            .failOnNoServerResponse(true)
            .singleNodeAddress("127.0.0.1", 1113)
            .build()

    eventStore.connect()

    val objectMapper = ObjectMapper()
    objectMapper.registerKotlinModule()

    return EventStoreEsjcEventRepositoryFactory(
            eventStore,
            objectMapper,
            "tests_$sessionId")

}
