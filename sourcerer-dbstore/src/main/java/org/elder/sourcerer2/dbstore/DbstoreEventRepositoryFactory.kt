package org.elder.sourcerer2.dbstore

import com.fasterxml.jackson.databind.ObjectMapper
import org.elder.sourcerer2.EventRepository
import org.elder.sourcerer2.EventRepositoryFactory
import org.elder.sourcerer2.EventTypeUtils
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

class DbstoreEventRepositoryFactory(
        private val eventStore: DbstoreEventStore,
        private val objectMapper: ObjectMapper,
        private val defaultNamespace: String,
        private val defaultShards: Int
) : EventRepositoryFactory {
    init {
        validateNamespace(defaultNamespace)
    }

    override fun <T> getEventRepository(eventType: Class<T>): EventRepository<T> {
        return getEventRepository(eventType, defaultNamespace, defaultShards)
    }

    override fun <T> getEventRepository(eventType: Class<T>, namespace: String): EventRepository<T> {
        return getEventRepository(eventType, namespace, defaultShards)
    }

    override fun <T> getEventRepository(
            eventType: Class<T>,
            namespace: String,
            shards: Int?
    ): EventRepository<T> {
        validateNamespace(namespace)
        val actualShards = validateShards(shards)
        val repositoryName = EventTypeUtils.getRepositoryName(eventType)
        val normalizer = EventTypeUtils.getNormalizer(eventType)
        val repositoryInfo = DbstoreRepositoryInfo(
                eventType = eventType,
                namespace = namespace,
                repository = repositoryName,
                shards = actualShards,
                normalizer = normalizer
        )
        logger.info(
                "Creating DbStore repository for {} with category {}",
                eventType.simpleName, repositoryInfo)
        return DbstoreEventRepository(repositoryInfo, eventStore, objectMapper)
    }

    private fun validateShards(shards: Int?): Int {
        return shards ?: throw IllegalArgumentException(
                "The DbStore Sourcerer repository requires a shard number to be specified"
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
