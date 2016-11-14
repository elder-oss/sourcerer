package org.elder.sourcerer;

import org.jetbrains.annotations.NotNull;

/**
 * An immutable specialization of AggregateState, implicitly bound to an aggregate projection, that
 * adds supports for updating state by applying events.
 *
 * @param <TState> The type representing state of an aggregate (as relevant to the domain where it
 *                 is used).
 * @param <TEvent> The type of events that can be applied to the aggregate (in the current domain).
 */
public interface ImmutableAggregate<TState, TEvent> extends AggregateState<TState, TEvent> {
    /**
     * Applies a single event to the aggregate state, updating both the encapsulated state, and the
     * local log of events applied to it.
     *
     * @param event The event to apply.
     * @return A new immutable aggregate state, representing the state of the aggregate with the
     * event applied, together with the list of applied events.
     */
    @NotNull
    ImmutableAggregate<TState, TEvent> apply(@NotNull TEvent event);

    /**
     * Applies a list of events to the aggregate state, updating both the encapsulated state,
     * and the local log of events applied to it.
     *
     * @param events The events to apply.
     * @return A new immutable aggregate state, representing the state of the aggregate with the
     * event applied, together with the list of applied events.
     */
    @NotNull
    ImmutableAggregate<TState, TEvent> apply(@NotNull Iterable<? extends TEvent> events);

    /**
     * Create a new aggregate instance containing the same state as the current one, but with a new
     * version - effectively representing the aggregate in a state after it has been persisted and
     * read back. The new source state will be the current state of the aggregate and the version
     * set to the specified value, and the list of pending events will be reset.
     * @param version The new version of the aggregate.
     * @return A new aggregate representing the aggregate as starting from the current state.
     */
    @NotNull
    ImmutableAggregate<TState, TEvent> rebase(int version);
}
