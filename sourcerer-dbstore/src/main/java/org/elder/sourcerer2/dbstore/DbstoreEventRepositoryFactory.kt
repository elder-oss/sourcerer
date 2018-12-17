package org.elder.sourcerer2.dbstore

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.base.Preconditions
import org.elder.sourcerer2.EventRepository
import org.elder.sourcerer2.EventRepositoryFactory
import org.elder.sourcerer2.EventTypeUtils
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

class DbstoreEventRepositoryFactory(
        private val eventStore: DbstoreEventStore,
        private val supportedShards: Int?,
        private val objectMapper: ObjectMapper,
        private val defaultNamespace: String
) : EventRepositoryFactory {

    init {
        Preconditions.checkNotNull(eventStore)
        Preconditions.checkNotNull(objectMapper)
        Preconditions.checkNotNull(defaultNamespace)

        validateNamespace(defaultNamespace)
    }

    override fun <T> getEventRepository(eventType: Class<T>): EventRepository<T> {
        return getEventRepository(eventType, defaultNamespace)
    }

    override fun <T> getEventRepository(
            eventType: Class<T>,
            namespace: String
    ): EventRepository<T> {
        validateNamespace(namespace)
        val repositoryName = EventTypeUtils.getRepositoryName(eventType)
        val normalizer = EventTypeUtils.getNormalizer(eventType)
        val category = String.format("%s:%s", namespace, repositoryName)
        logger.info(
                "Creating DbStore repository for {} with category {}",
                eventType.simpleName, category)
        return DbstoreEventRepository(
                eventType,
                supportedShards,
                category,
                eventStore,
                objectMapper,
                normalizer
        )
    }

    companion object {
        private val NAMESPACE_REGEX = Pattern.compile("[a-zA-Z][a-zA-Z_0-9]*")
        private val logger = LoggerFactory.getLogger(DbstoreEventRepositoryFactory::class.java)

        private fun validateNamespace(defaultNamespace: String) {
            if (!NAMESPACE_REGEX.matcher(defaultNamespace).matches()) {
                throw IllegalArgumentException(
                        "Invalid namespace, namespaces cannot include - / : " + "or other special characters")
            }
        }
    }
}
