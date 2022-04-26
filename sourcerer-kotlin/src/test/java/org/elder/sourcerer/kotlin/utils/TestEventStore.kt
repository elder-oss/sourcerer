package org.elder.sourcerer.kotlin.utils

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.msemys.esjc.EventStoreBuilder
import org.elder.sourcerer.AggregateProjection
import org.elder.sourcerer.AggregateRepository
import org.elder.sourcerer.DefaultAggregateRepository
import org.elder.sourcerer.EventRepository
import org.elder.sourcerer.esjc.EventStoreEsjcEventRepositoryFactory
import kotlin.reflect.KClass

class TestEventStore(
        private val host: String = "127.0.0.1",
        private val port: Int = API_PORT
) {
    inline fun <STATE, reified EVENT : Any> createAggregateRepository(
            namespace: String,
            projection: AggregateProjection<STATE, EVENT>
    ): AggregateRepository<STATE, EVENT> {
        val eventRepository = createEventRepository(EVENT::class, namespace)
        return DefaultAggregateRepository(eventRepository, projection)
    }

    fun <STATE, EVENT : Any> createAggregateRepository(
            clazz: KClass<EVENT>,
            namespace: String,
            projection: AggregateProjection<STATE, EVENT>
    ): AggregateRepository<STATE, EVENT> {
        val eventRepository = createEventRepository(clazz, namespace)
        return DefaultAggregateRepository(eventRepository, projection)
    }

    inline fun <reified T : Any> createEventRepository(namespace: String): EventRepository<T> {
        return createEventRepository(T::class, namespace)
    }

    fun <T : Any> createEventRepository(
            clazz: KClass<T>,
            namespace: String
    ): EventRepository<T> {
        val eventStore = EventStoreBuilder
                .newBuilder()
                .userCredentials("admin", "changeit")
                .requireMaster(false)
                .singleNodeAddress(host, port)
                .failOnNoServerResponse(true)
                .build()
        return EventStoreEsjcEventRepositoryFactory(eventStore, objectMapper(), namespace)
                .getEventRepository(clazz.java, namespace)
    }

    private fun objectMapper(): ObjectMapper {
        val mapper = ObjectMapper()
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        mapper.registerKotlinModule()
        return mapper
    }

    companion object {
        private const val API_PORT = 1113
    }
}
