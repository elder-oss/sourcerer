package org.elder.sourcerer;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An aggregate state is a description of an aggregate (an entity constructed from events) as it
 * was created from a known state and optionally with additional modifications on top, applied in
 * the form of events.
 * <p>
 * Aggregates provide a convenient alternative to dealing with only events directly, and can be
 * used to track state changes and events as a unit. See ImmutableAggregate and MutableAggregate
 * for implementations of this interface that supports applying new events.
 * <p>
 * The aggregate state contains a log of events applied to it so far, as well as the current state
 * of the aggregate with the events applied. Until the events are explicitly persisted (manually
 * or through a command), they are only kept in-process and the aggregate state represents a
 * "dry run" result of committing the events.
 * <p>
 * A concrete aggregate is usually bound to an AggregateProjection, that provides the logic for
 * how to append the state of the aggregate given events.
 *
 * @param <TState> The type representing state of an aggregate (as relevant to the domain where it
 *                 is used).
 * @param <TEvent> The type of events that can be applied to the aggregate (in the current domain).
 */
public interface AggregateState<TState, TEvent> {
    int VERSION_NOT_CREATED = -1;

    /**
     * Gets the id of the aggregate being operated on. The id will never be updated by events
     * being applied to the aggregate state.
     *
     * @return The id of the aggregate this state represents.
     */
    @NotNull
    @Contract(pure = true)
    String id();

    /**
     * Gets the version of the aggregate at the point at which it was constructed or loaded from an
     * external source. This version will not be updated as new events are applied, until it is
     * also successfully persisted.
     *
     * @return The version of the aggregate as loaded from an external source, prior to events
     * applied to it locally. Will be -1 if not created.
     */
    @Contract(pure = true)
    int sourceVersion();

    /**
     * Gets the state of the aggregate as loaded from an external source. Will be null if the
     * aggregate did not already exist.
     *
     * @return The original state of the aggregate before the events were applied.
     */
    @Nullable
    @Contract(pure = true)
    TState sourceState();

    /**
     * Gets the current state of the aggregate, after the events have been applied.
     *
     * @return The state of the aggregate, with the events of this aggregate state holder applied.
     */
    TState state();

    /**
     * Gets the events that have been applied to the aggregate state so far, to take it from the
     * original state to the current state.
     *
     * @return The events applied to the aggregate.
     */
    @NotNull
    List<TEvent> events();

    /**
     * Returns an immutable aggregate with the values from the currently aggregate state.
     *
     * @return An immutable aggregate.
     */
    ImmutableAggregate<TState, TEvent> toImmutableAggregate();

    /**
     * Returns a mutable aggregate with the values from the currently aggregate state.
     *
     * @return A mutable aggregate.
     */
    MutableAggregate<TState, TEvent> toMutableAggregate();
}
