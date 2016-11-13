package org.elder.sourcerer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An aggregate state is an immutable representation of the state of an aggregate, along with events
 * that have been applied to bring it to its current state from an earlier previous state. An
 * aggregate state can be used as an alternative to simply returning events from an operation when
 * multiple stages of intermediate state are required as an operation is executed.
 *
 * The aggregate state contains a log of events applied to it so far, as well as the current state
 * of the aggregate with the events applied. Until the events are explicitly persisted (manually
 * or through a command), they are only kept in-process and the aggregate state represents a
 * "dry run" result of committing the events.
 *
 * An aggregate state is implicitly bound to an AggregateProjection, that provides the logic for
 * how to update the state of the aggregate given events.
 *
 * See also MutableAggregateState for a mutable variation on this class.
 *
 * @param <TState> The type representing state of an aggregate (as relevant to the domain where it
 *                 is used).
 * @param <TEvent> The type of events that can be applied to the aggregate (in the current domain).
 */
public interface AggregateState<TState, TEvent> {
    /**
     * Applies a single event to the aggregate state, updating both the encapsulated state, and the
     * local log of events applied to it.
     * @param event The event to apply.
     * @return A new immutable aggregate state, representing the state of the aggregate with the
     * event applied, together with the list of applied events.
     */
    @NotNull
    AggregateState<TState, TEvent> apply(@NotNull TEvent event);

    /**
     * Applies a list of events to the aggregate state, updating both the encapsulated state,
     * and the local log of events applied to it.
     * @param events The events to apply.
     * @return A new immutable aggregate state, representing the state of the aggregate with the
     * event applied, together with the list of applied events.
     */
    @NotNull
    AggregateState<TState, TEvent> apply(@NotNull Iterable<? extends TEvent> events);

    /**
     * Gets the id of the aggregate being operated on. The id will never be updated by events
     * being applied to the aggregate state.
     * @return The id of the aggregate this state represents.
     */
    String id();

    /**
     * Gets the state of the aggregate at the time the aggregate state holder was first created,
     * may be null.
     * @return The original state of the aggregate before the events were applied.
     */
    @Nullable
    TState originalState();

    /**
     * Gets the current state of the aggregate, after the events have been applied.
     * @return The state of the aggregate, with the events of this aggregate state holder applied.
     */
    @Nullable
    TState state();

    /**
     * Gets the events that have been applied to the aggregate state so far, to take it from the
     * original state to the current state.
     * @return The events applied to the aggregate.
     */
    @NotNull
    List<TEvent> events();

    /**
     * Returns a mutable aggregate state from the current immutable state.
     * @return A mutable aggregate state.
     */
    MutableAggregateState<TState, TEvent> toMutable();
}
