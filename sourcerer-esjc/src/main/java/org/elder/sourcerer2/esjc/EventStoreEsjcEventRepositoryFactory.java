package org.elder.sourcerer2.esjc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.msemys.esjc.EventStore;
import com.google.common.base.Preconditions;
import org.elder.sourcerer2.EventNormalizer;
import org.elder.sourcerer2.EventRepository;
import org.elder.sourcerer2.EventRepositoryFactory;
import org.elder.sourcerer2.EventTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class EventStoreEsjcEventRepositoryFactory implements EventRepositoryFactory {
    private static final Pattern NAMESPACE_REGEX = Pattern.compile("[a-zA-Z][a-zA-Z_0-9]*");
    private static final Logger logger
            = LoggerFactory.getLogger(EventStoreEsjcEventRepositoryFactory.class);

    private final EventStore eventStore;
    private final ObjectMapper objectMapper;
    private final String defaultNamespace;

    public EventStoreEsjcEventRepositoryFactory(
            final EventStore eventStore,
            final ObjectMapper objectMapper,
            final String defaultNamespace) {
        Preconditions.checkNotNull(eventStore);
        Preconditions.checkNotNull(objectMapper);
        Preconditions.checkNotNull(defaultNamespace);

        validateNamespace(defaultNamespace);
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
        this.defaultNamespace = defaultNamespace;
    }

    @Override
    public <T> EventRepository<T> getEventRepository(final Class<T> eventType) {
        return getEventRepository(eventType, defaultNamespace);
    }

    @Override
    public <T> EventRepository<T> getEventRepository(
            final Class<T> eventType,
            final String namespace) {
        return getEventRepository(eventType, namespace, null);
    }

    @Override
    public <T> EventRepository<T> getEventRepository(
            final Class<T> eventType,
            final String namespace,
            final Integer shards
    ) {
        validateNamespace(namespace);
        if (shards != null) {
            throw new IllegalArgumentException("The ESJC repository does not support sharding");
        }

        String repositoryName = EventTypeUtils.getRepositoryName(eventType);
        EventNormalizer<T> normalizer = EventTypeUtils.getNormalizer(eventType);
        String eventStreamPrefix = String.format("%s:%s", namespace, repositoryName);
        logger.info(
                "Creating Event Store repository for {} with prefix {}",
                eventType.getSimpleName(), eventStreamPrefix);
        return new EventStoreEsjcEventRepository<>(
                eventStreamPrefix, eventStore, eventType, objectMapper, normalizer);
    }

    private static void validateNamespace(final String defaultNamespace) {
        if (!NAMESPACE_REGEX.matcher(defaultNamespace).matches()) {
            throw new IllegalArgumentException(
                    "Invalid namespace, namespaces cannot include - / : "
                            + "or other special characters");
        }
    }
}
