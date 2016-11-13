package org.elder.sourcerer;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Default sourcerer implementation of AggregateStateFactory.
 */
public class DefaultAggregateStateFactory<TState, TEvent>
        implements AggregateStateFactory<TState, TEvent> {
    private final AggregateProjection<TState, TEvent> projection;

    public DefaultAggregateStateFactory(
            @NotNull
            final AggregateProjection<TState, TEvent> projection) {
        Preconditions.checkNotNull(projection);
        this.projection = projection;
    }

    @Override
    public AggregateState<TState, TEvent> fromId(@NotNull String id) {
        return fromState(id, null);
    }

    @Override
    public AggregateState<TState, TEvent> fromState(@NotNull String id, @Nullable TState state) {
        return new DefaultAggregateState<>(projection, id, state);
    }

    @Override
    public MutableAggregateState<TState, TEvent> mutableFromId(@NotNull String id) {
        return mutableFromState(id, null);
    }

    @Override
    public MutableAggregateState<TState, TEvent> mutableFromState(
            @NotNull String id, @Nullable TState state) {
        return new DefaultMutableAggregateState<>(projection, id, state);
    }
}
