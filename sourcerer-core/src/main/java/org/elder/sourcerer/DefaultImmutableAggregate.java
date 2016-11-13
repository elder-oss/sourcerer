package org.elder.sourcerer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
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
    private final int originalVersion;
    private final TState originalState;
    private final TState state;
    private final ImmutableList<TEvent> appliedEvents;

    public DefaultImmutableAggregate(
            @NotNull final AggregateProjection<TState, TEvent> projection,
            @NotNull final String id,
            final int originalVersion,
            @Nullable final TState originalState) {
        this(projection, id, originalVersion, originalState, originalState, ImmutableList.of());
    }

    public DefaultImmutableAggregate(
            @NotNull final AggregateProjection<TState, TEvent> projection,
            @NotNull final String id,
            final int originalVersion,
            @Nullable final TState originalState,
            @Nullable final TState state,
            @NotNull final List<TEvent> events) {
        Preconditions.checkNotNull(projection);
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(events);
        this.projection = projection;
        this.id = id;
        this.originalVersion = originalVersion;
        this.originalState = originalState;
        this.state = state;
        this.appliedEvents = ImmutableList.copyOf(events);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int originalVersion() {
        return originalVersion;
    }

    @Nullable
    @Override
    public TState originalState() {
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
                originalVersion,
                originalState,
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
                originalVersion,
                originalState,
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
                originalVersion,
                originalState,
                newState,
                ImmutableList.<TEvent>builder().addAll(appliedEvents).addAll(events).build());
    }
}
