package org.elder.sourcerer2;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public final class EventTypeUtils {
    private EventTypeUtils() {
    }

    /**
     * Gets the configured repository for a given event type, assumed to be annotated with
     *
     * @param eventType The base type of events to extract a repository name for.
     */
    public static String getRepositoryName(final Class<?> eventType) {
        EventType eventTypeData = getEventTypeAnnotation(eventType);

        if (eventTypeData.repositoryName() == null) {
            throw new IllegalArgumentException(
                    "Provided event type does not have a configured repository name");
        }

        return eventTypeData.repositoryName();
    }

    @SuppressWarnings("unchecked")
    public static <T> EventNormalizer<T> getNormalizer(final Class<T> eventType) {
        EventType eventTypeData = getEventTypeAnnotation(eventType);

        if (eventTypeData.normalizer() != null
                && !eventTypeData.normalizer().equals(EventType.NoNormalizer.class)) {
            return (EventNormalizer) createInstance(eventTypeData.normalizer());
        } else {
            return null;
        }
    }

    public static EventType getEventTypeAnnotation(final Class<?> eventType) {
        if (!eventType.isAnnotationPresent(EventType.class)) {
            throw new IllegalArgumentException(
                    "Provided event type does not have an EvenType annotation");
        }

        return eventType.getAnnotation(EventType.class);
    }

    private static EventNormalizer<?> createInstance(
            final Class<? extends EventNormalizer<?>> normalizer) {
        try {
            Constructor<? extends EventNormalizer<?>> constructor
                    = normalizer.getDeclaredConstructor();
            return constructor.newInstance();
        } catch (NoSuchMethodException | IllegalAccessException
                | InvocationTargetException | InstantiationException ex) {
            throw new IllegalArgumentException(
                    "Normalizer must have a public, no-args constructor",
                    ex);
        }
    }
}
