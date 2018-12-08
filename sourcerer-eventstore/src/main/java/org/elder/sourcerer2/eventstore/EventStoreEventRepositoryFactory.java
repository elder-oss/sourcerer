package org.elder.sourcerer2.eventstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import eventstore.j.EsConnection;
import org.elder.sourcerer2.EventNormalizer;
import org.elder.sourcerer2.EventRepository;
import org.elder.sourcerer2.EventRepositoryFactory;
import org.elder.sourcerer2.EventTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class EventStoreEventRepositoryFactory implements EventRepositoryFactory {
    private static final Pattern NAMESPACE_REGEX = Pattern.compile("[a-zA-Z][a-zA-Z_0-9]*");

    private static final Logger logger
            = LoggerFactory.getLogger(EventStoreEventRepositoryFactory.class);

    private final EsConnection connection;
    private final ObjectMapper objectMapper;
    private final String defaultNamespace;

    public EventStoreEventRepositoryFactory(
            final EsConnection connection,
            final ObjectMapper objectMapper,
            final String defaultNamespace) {
        Preconditions.checkNotNull(connection);
        Preconditions.checkNotNull(objectMapper);
        Preconditions.checkNotNull(defaultNamespace);

        validateNamespace(defaultNamespace);
        this.connection = connection;
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
        validateNamespace(namespace);
        String repositoryName = EventTypeUtils.getRepositoryName(eventType);
        EventNormalizer<T> normalizer = EventTypeUtils.getNormalizer(eventType);
        String eventStreamPrefix = String.format("%s:%s", namespace, repositoryName);
        logger.info(
                "Creating Event Store repository for {} with prefix {}",
                eventType.getSimpleName(), eventStreamPrefix);
        return new EventStoreEventRepository<>(
                eventStreamPrefix, connection, eventType, objectMapper, normalizer);
    }

    private static void validateNamespace(final String defaultNamespace) {
        if (!NAMESPACE_REGEX.matcher(defaultNamespace).matches()) {
            throw new IllegalArgumentException(
                    "Invalid namespace, namespaces cannot include - / : "
                            + "or other special characters");
        }
    }
}
