package org.elder.sourcerer2.dbstore

import org.elder.sourcerer2.EventRepositoryFactory
import org.elder.sourcerer2.EventTypeUtils

class DbstoreEventRepositoryFactory(
        private val eventStore: EventStore,
        private val objectMapper: ObjectMapper,
        private val defaultNamespace: String) : EventRepositoryFactory {

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
            namespace: String): EventRepository<T> {
        validateNamespace(namespace)
        val repositoryName = EventTypeUtils.getRepositoryName(eventType)
        val normalizer = EventTypeUtils.getNormalizer(eventType)
        val eventStreamPrefix = String.format("%s:%s", namespace, repositoryName)
        logger.info(
                "Creating Event Store repository for {} with prefix {}",
                eventType.simpleName, eventStreamPrefix)
        return EventStoreEsjcEventRepository<T>(
                eventStreamPrefix, eventStore, eventType, objectMapper, normalizer)
    }

    companion object {
        private val NAMESPACE_REGEX = Pattern.compile("[a-zA-Z][a-zA-Z_0-9]*")

        private val logger = LoggerFactory.getLogger(EventStoreEsjcEventRepositoryFactory::class.java)

        private fun validateNamespace(defaultNamespace: String) {
            if (!NAMESPACE_REGEX.matcher(defaultNamespace).matches()) {
                throw IllegalArgumentException(
                        "Invalid namespace, namespaces cannot include - / : " + "or other special characters")
            }
        }
    }
}
