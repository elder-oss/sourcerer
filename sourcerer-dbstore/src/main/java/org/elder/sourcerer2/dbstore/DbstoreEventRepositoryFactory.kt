package org.elder.sourcerer2.dbstore

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.newCoroutineContext
import org.elder.sourcerer2.EventRepository
import org.elder.sourcerer2.EventRepositoryFactory
import org.elder.sourcerer2.EventTypeUtils
import org.slf4j.LoggerFactory
import java.util.regex.Pattern
import kotlin.coroutines.CoroutineContext

class DbstoreEventRepositoryFactory @JvmOverloads constructor(
        private val eventStore: DbstoreEventStore,
        private val objectMapper: ObjectMapper,
        private val defaultNamespace: String,
        override val coroutineContext: CoroutineContext = GlobalScope.newCoroutineContext(Dispatchers.IO)
) : EventRepositoryFactory, CoroutineScope {
    override fun <T> getEventRepository(eventType: Class<T>): EventRepository<T> {
        return getEventRepository(eventType, defaultNamespace)
    }

    override fun <T> getEventRepository(eventType: Class<T>, namespace: String): EventRepository<T> {
        validateNamespace(namespace)
        val repositoryName = EventTypeUtils.getRepositoryName(eventType)
        val normalizer = EventTypeUtils.getNormalizer(eventType)
        val repositoryInfo = DbstoreRepositoryInfo(
                eventType = eventType,
                namespace = namespace,
                repository = repositoryName,
                normalizer = normalizer
        )
        logger.info(
                "Creating DbStore repository for {} with category {}",
                eventType.simpleName, repositoryInfo)
        return DbstoreEventRepository(
                repositoryInfo,
                eventStore,
                objectMapper,
                this
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
