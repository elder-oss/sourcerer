package org.elder.sourcerer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DefaultMutableAggregate<TState, TEvent>
        implements MutableAggregate<TState, TEvent> {
    private final AggregateProjection<TState, TEvent> projection;
    private final String id;
    private final TState originalState;
    private final int originalVersion;
    private TState state;
    private final List<TEvent> appliedEvents;

    public DefaultMutableAggregate(
            @NotNull final AggregateProjection<TState, TEvent> projection,
            @NotNull final String id,
            final int originalVersion,
            @Nullable final TState originalState) {
        this(projection, id, originalVersion, originalState, originalState, ImmutableList.of());
    }

    public DefaultMutableAggregate(
            @NotNull final AggregateProjection<TState, TEvent> projection,
            @NotNull final String id,
            final int originalVersion,
            @Nullable final TState originalState,
            @Nullable final TState state,
            @NotNull final List<TEvent> events) {
        Preconditions.checkNotNull(projection);
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(state);
        Preconditions.checkNotNull(events);
        this.projection = projection;
        this.id = id;
        this.originalVersion = originalVersion;
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
    @NotNull
    @Contract(pure = true)
    public String id() {
        return id;
    }

    @Override
    public int sourceVersion() {
        return originalVersion;
    }

    @Nullable
    @Override
    public TState sourceState() {
        return originalState;
    }

    @Nullable
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
    public ImmutableAggregate<TState, TEvent> toImmutableAggregate() {
        return new DefaultImmutableAggregate<>(
                projection,
                id,
                originalVersion,
                originalState,
                state,
                appliedEvents);
    }

    @Override
    public MutableAggregate<TState, TEvent> toMutableAggregate() {
        return new DefaultMutableAggregate<>(
                projection,
                id,
                originalVersion,
                originalState,
                state,
                appliedEvents);
    }
}
