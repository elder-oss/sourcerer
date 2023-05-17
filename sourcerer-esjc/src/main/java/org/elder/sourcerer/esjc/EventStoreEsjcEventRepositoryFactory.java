package org.elder.sourcerer.esjc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.msemys.esjc.EventStore;
import com.google.common.base.Preconditions;
import org.elder.sourcerer.EventNormalizer;
import org.elder.sourcerer.EventRepository;
import org.elder.sourcerer.EventRepositoryFactory;
import org.elder.sourcerer.EventTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.regex.Pattern;

public class EventStoreEsjcEventRepositoryFactory implements EventRepositoryFactory {
    private static final Pattern NAMESPACE_REGEX = Pattern.compile("[a-zA-Z][a-zA-Z_0-9]*");
    private static final Pattern REPOSITORY_NAME_REGEX = Pattern.compile("[a-zA-Z][a-zA-Z_0-9]*");

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
            final String repositoryName) {
        validateNamespace(namespace);
        validateRepositoryName(repositoryName);
        String actualRepositoryName = repositoryName(eventType, repositoryName);
        EventNormalizer<T> normalizer = EventTypeUtils.getNormalizer(eventType);
        String eventStreamPrefix = String.format("%s:%s", namespace, actualRepositoryName);
        logger.info(
                "Creating Event Store repository for {} with prefix {}",
                eventType.getSimpleName(), eventStreamPrefix);
        return new EventStoreEsjcEventRepository<>(
                eventStreamPrefix, eventStore, eventType, objectMapper, normalizer);
    }

    private <T> String repositoryName(
            final Class<T> eventType,
            final String repositoryName
    ) {
        if (repositoryName != null) {
            return repositoryName;
        } else {
            return EventTypeUtils.getRepositoryName(eventType);
        }
    }

    private static void validateRepositoryName(final String repositoryName) {
        if (!REPOSITORY_NAME_REGEX.matcher(repositoryName).matches()) {
            throw new IllegalArgumentException(
                    "Invalid repository name, name cannot include - / : "
                            + "or other special characters");
        }
    }

    @Override
    public void close() {
        eventStore.shutdown();
    }

    private static void validateNamespace(final String defaultNamespace) {
        if (!NAMESPACE_REGEX.matcher(defaultNamespace).matches()) {
            throw new IllegalArgumentException(
                    "Invalid namespace, namespaces cannot include - / : "
                            + "or other special characters");
        }
    }
}
