package org.elder.sourcerer;

/**
 * The event normalizer is an optional configuration option on an event, used to migrate legacy
 * data representations to a sanitized one (as historical events cannot be modified).
 * @param <T> The event type to operate on.
 */
@FunctionalInterface
public interface EventNormalizer<T> {
    /**
     * Normalize an individual event, returning a cleaned up version of the input. The
     * implementation should return an updated copy of the event rather than modifying it in place,
     * but may return the input as is if no change is needed.
     * @param event The event to normalize.
     * @return A normalized event.
     */
    T normalizeEvent(T event);
}
