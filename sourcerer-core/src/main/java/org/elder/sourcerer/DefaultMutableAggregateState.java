package org.elder.sourcerer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DefaultMutableAggregateState<TState, TEvent>
        implements MutableAggregateState<TState, TEvent> {
    private final AggregateProjection<TState, TEvent> projection;
    private final String id;
    private final TState originalState;
    private TState state;
    private final List<TEvent> appliedEvents;

    public DefaultMutableAggregateState(
            @NotNull final AggregateProjection<TState, TEvent> projection,
            @NotNull final String id,
            @Nullable final TState originalState) {
        this(projection, id, originalState, null, ImmutableList.of());
    }

    public DefaultMutableAggregateState(
            @NotNull final AggregateProjection<TState, TEvent> projection,
            @NotNull final String id,
            @Nullable final TState originalState,
            @Nullable final TState state,
            @NotNull final List<TEvent> events) {
        Preconditions.checkNotNull(projection);
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(state);
        Preconditions.checkNotNull(events);
        this.projection = projection;
        this.id = id;
        this.originalState = originalState;
        this.state = state;
        this.appliedEvents = new ArrayList<>(events);
    }

    @Override
    public void apply(@NotNull final TEvent event) {
        Preconditions.checkNotNull(event);
        state = projection.apply(id, state, event);
        appliedEvents.add(event);
    }

    @Override
    public void apply(@NotNull final Iterable<? extends TEvent> events) {
        Preconditions.checkNotNull(events);
        state = projection.apply(id, state, events);
        events.forEach(appliedEvents::add);
    }

    @Override
    public String id() {
        return id;
    }

    @Nullable
    @Override
    public TState originalState() {
        return originalState;
    }

    @NotNull
    @Override
    public TState state() {
        return state;
    }

    @NotNull
    @Override
    public List<TEvent> events() {
        return ImmutableList.copyOf(appliedEvents);
    }

    @Override
    public AggregateState<TState, TEvent> toImmutable() {
        return new DefaultAggregateState<>(
                projection,
                id,
                originalState,
                state,
                appliedEvents);
    }
}
