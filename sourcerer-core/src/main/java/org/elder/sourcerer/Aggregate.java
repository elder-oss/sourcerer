package org.elder.sourcerer;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An aggregate is an entity constructed from events, with an id, the version it was constructed
 * from (if loaded from an existing persisted state), and a log of the events applied since loaded.
 * <p>
 * Aggregates provide a convenient alternative to dealing with events directly, and can be used to
 * track state changes and events as a unit. See ImmutableAggregate and MutableAggregate for
 * implementations of this interface that supports applying new events.
 * <p>
 * The aggregate state contains a log of events applied to it so far, as well as the current state
 * of the aggregate with the events applied. Until the events are explicitly persisted (manually or
 * through a command), they are only kept in-process and the aggregate state represents a "dry run"
 * result of committing the events.
 * <p>
 * A concrete aggregate is usually bound to an AggregateProjection, that provides the logic for how
 * to append the state of the aggregate given events.
 *
 * @param <TState> The type of the state of the aggregate.
 * @param <TEvent> The type of events that can be applied to the aggregate.
 */
public interface Aggregate<TState, TEvent> {
    int VERSION_NOT_CREATED = -1;

    /**
     * Gets the id of the aggregate being operated on. None of the methods on Aggregate will modify
     * this value once an instance has been created.
     *
     * @return The id of the aggregate.
     */
    @NotNull
    @Contract(pure = true)
    String id();

    /**
     * Gets the version of the aggregate at the point at which it was constructed or loaded from an
     * external source. Applying new events to an aggregate will not change this value until the
     * aggregate is explicitly marked as persisted (see rebase).
     *
     * @return The version of the aggregate as loaded from an external source, prior to events
     * applied to it locally. Will be -1 if the aggregate is new.
     */
    @Contract(pure = true)
    int sourceVersion();

    /**
     * Gets the current state of the aggregate, including the effects of events applied locally.
     *
     * @return The state of the aggregate, with the events of this aggregate state holder applied.
     */
    @NotNull
    @Contract(pure = true)
    TState state();

    /**
     * Gets the events that have been applied to the aggregate state so far, to take it from the
     * source state to the current state.
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
    ImmutableAggregate<TState, TEvent> toImmutable();

    /**
     * Returns a mutable aggregate with the values from the currently aggregate state.
     *
     * @return A mutable aggregate.
     */
    MutableAggregate<TState, TEvent> toMutable();
}
