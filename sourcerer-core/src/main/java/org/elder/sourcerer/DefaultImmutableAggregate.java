package org.elder.sourcerer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Default sourcerer implementation of ImmutableAggregate.
 */
public class DefaultImmutableAggregate<TState, TEvent>
        implements ImmutableAggregate<TState, TEvent> {
    private final AggregateProjection<TState, TEvent> projection;
    private final String id;
    private final int sourceVersion;
    private final TState sourceState;
    private final TState state;
    private final ImmutableList<TEvent> appliedEvents;

    public DefaultImmutableAggregate(
            @NotNull final AggregateProjection<TState, TEvent> projection,
            @NotNull final String id,
            final int sourceVersion,
            @Nullable final TState sourceState) {
        this(
                projection,
                id,
                sourceVersion,
                sourceState,
                sourceState != null ? sourceState : projection.empty(),
                ImmutableList.of());
    }

    public DefaultImmutableAggregate(
            @NotNull final AggregateProjection<TState, TEvent> projection,
            @NotNull final String id,
            final int sourceVersion,
            @Nullable final TState sourceState,
            @NotNull final TState state,
            @NotNull final List<TEvent> events) {
        Preconditions.checkNotNull(projection);
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(events);
        this.projection = projection;
        this.id = id;
        this.sourceVersion = sourceVersion;
        this.sourceState = sourceState;
        this.state = state;
        this.appliedEvents = ImmutableList.copyOf(events);
    }

    @Override
    @NotNull
    @Contract(pure = true)
    public String id() {
        return id;
    }

    @Override
    public int sourceVersion() {
        return sourceVersion;
    }

    @Nullable
    @Override
    public TState sourceState() {
        return sourceState;
    }

    @Override
    @NotNull
    @Contract(pure = true)
    public TState state() {
        return state;
    }

    @Override
    @NotNull
    @Contract(pure = true)
    public List<TEvent> events() {
        return appliedEvents;
    }

    @Override
    public ImmutableAggregate<TState, TEvent> toImmutableAggregate() {
        return this;
    }

    @Override
    public MutableAggregate<TState, TEvent> toMutableAggregate() {
        return new DefaultMutableAggregate<>(
                projection,
                id,
                sourceVersion,
                sourceState,
                state,
                appliedEvents);
    }

    @NotNull
    @Override
    public ImmutableAggregate<TState, TEvent> apply(@NotNull final TEvent event) {
        Preconditions.checkNotNull(event);
        @NotNull TState newState = projection.apply(id, state, event);
        return new DefaultImmutableAggregate<>(
                projection,
                id,
                sourceVersion,
                sourceState,
                newState,
                ImmutableList.<TEvent>builder().addAll(appliedEvents).add(event).build());
    }

    @NotNull
    @Override
    public ImmutableAggregate<TState, TEvent> apply(
            @NotNull final Iterable<? extends TEvent> events) {
        Preconditions.checkNotNull(events);
        @NotNull TState newState = projection.apply(id, state, events);
        return new DefaultImmutableAggregate<>(
                projection,
                id,
                sourceVersion,
                sourceState,
                newState,
                ImmutableList.<TEvent>builder().addAll(appliedEvents).addAll(events).build());
    }
}
