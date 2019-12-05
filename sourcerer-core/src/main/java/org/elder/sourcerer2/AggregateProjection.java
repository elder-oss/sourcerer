package org.elder.sourcerer2;

import org.jetbrains.annotations.NotNull;

/**
 * A projection is a function that given an aggregate state and an event, returns a new aggregate
 * state representing the aggregate with the event applied.
 *
 * @param <TState> The type of aggregate state that the projection operates on
 * @param <TEvent> The type of event that the projection is capable of applying to aggregates.
 */
public interface AggregateProjection<TState, TEvent> {
    /**
     * Returns an empty aggregate state for the given type. The projection is never passed null as
     * the current state, but rather this value in the case where the event(s) applied are the very
     * first ones.
     *
     * @return An empty state for the aggregate.
     */
    @NotNull TState empty();

    /**
     * Applies an event to an aggregate, creating a new aggregate representing a snapshot-in-time
     * state of the aggregate with the event applied. This must be a pure function, returning a
     * semantically equivalent new aggregate state given the same (or semantically equivalent)
     * aggregate and event.
     * <p>
     * All types are expected to be immutable, the function must return a new aggregate instance and
     * must not attempt to mutate either the given aggregate or event. The implementation should
     * have no externally visible side effects or depend on external state.
     * <p>
     * Unlike commands, there is no mechanism by which events can be rejected as they represent past
     * tense, already happened events. The projection must be able to deal with any event type and
     * values of event types that may have been created in the past, substituting sane defaults for
     * missing values that may be required in later versions of the event.
     *
     * @param id    The id of the aggregate being projected.
     * @param state A snapshot in time state of an aggregate.
     * @param event The event to apply to the aggregate.
     * @return A new aggregate instance of the same type as the provided, representing a
     * snapshot-in-time state of the given aggregate with the provided event applied.
     */
    @NotNull TState apply(@NotNull StreamId id, @NotNull TState state, @NotNull TEvent event);

    /**
     * Applies a sequence of events to an aggregate, creating a new aggregate representing a
     * snapshot-in-time state of the aggregate with the events applied.
     * <p>
     * The implementation of this function must be semantically equivalent to a left fold of the
     * single event apply function over the state and sequence of events.
     * <p>
     * This must be a pure function, returning a semantically equivalent new aggregate state given
     * the same (or semantically equivalent) aggregate and event.
     * <p>
     * All types are expected to be immutable, the function must return a new aggregate instance and
     * must not attempt to mutate either the given aggregate or event. The implementation should
     * have no externally visible side effects or depend on external state.
     * <p>
     * Unlike commands, there is no mechanism by which events can be rejected as they represent past
     * tense, already happened events. The projection must be able to deal with any event type and
     * values of event types that may have been created in the past, substituting sane defaults for
     * missing values that may be required in later versions of the event.
     *
     * @param id     The id of the aggregate being projected.
     * @param state  A snapshot in time state of an aggregate.
     * @param events The events to apply to the aggregate.
     * @return A new aggregate instance of the same type as the provided, representing a
     * snapshot-in-time state of the given aggregate with the provided event applied.
     */
    @NotNull default TState apply(
            @NotNull final StreamId id,
            @NotNull final TState state,
            @NotNull final Iterable<? extends TEvent> events) {
        TState updatedState = state;
        for (TEvent e : events) {
            updatedState = apply(id, updatedState, e);
        }
        return updatedState;
    }
}
