package org.elder.sourcerer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for instantiating immutable or mutable aggregate state holders from a current state
 * (that may be null). The default implementation is implicitly bound to an aggregate projection
 * allowing for events to be applied and state updated correctly.
 */
public interface AggregateStateFactory<TState, TEvent> {
    /**
     * Creates a new immutable aggregate state holder from an aggregate and no (null) current state.
     * @param id The id of the aggregate to encapsulate.
     * @return An immutable state holder representing the aggregate with no new events applied.
     */
    AggregateState<TState, TEvent> fromId(@NotNull String id);

    /**
     * Creates a new immutable aggregate state holder from an aggregate and current state.
     * @param id The id of the aggregate to encapsulate.
     * @return An immutable state holder representing the aggregate with no new events applied.
     */
    AggregateState<TState, TEvent> fromState(@NotNull String id, @Nullable TState state);

    /**
     * Creates a new mutable aggregate state holder from an aggregate and no (null) current state.
     * @param id The id of the aggregate to encapsulate.
     * @return An mutable state holder representing the aggregate with no new events applied.
     */
    MutableAggregateState<TState, TEvent> mutableFromId(@NotNull String id);

    /**
     * Creates a new mutable aggregate state holder from an aggregate and current state.
     * @param id The id of the aggregate to encapsulate.
     * @return A mutable state holder representing the aggregate with no new events applied.
     */
    MutableAggregateState<TState, TEvent> mutableFromState(
            @NotNull String id,
            @Nullable TState state);
}
