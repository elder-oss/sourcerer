package org.elder.sourcerer2;

/**
 * Factory of event repositories for a given event type.
 */
public interface EventRepositoryFactory {
    /**
     * Gets an event repository for the given type of events, and the default namespace.
     *
     * @param eventType The runtime Class representing the base type of events for which to create
     *                  an event repository.
     * @param <T>       The base type for events in the event repository.
     * @return An event repository instance used to access events of the given type.
     */
    <T> EventRepository<T> getEventRepository(Class<T> eventType);

    /**
     * Gets an event repository for the given type of events, and a given namespace.
     *
     * @param eventType The runtime Class representing the base type of events for which to create
     *                  an event repository.
     * @param namespace An namespace for the events. The exact meaning is implementation specific,
     *                  but given the same parameters and different namespaces, the implementation
     *                  should provide two completely independent stores of events.
     * @param <T>       The base type for events in the event repository.
     * @return An event repository instance used to access events of the given type.
     */
    <T> EventRepository<T> getEventRepository(Class<T> eventType, String namespace);
}
