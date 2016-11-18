package org.elder.sourcerer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DefaultMutableAggregate<TState, TEvent>
        implements MutableAggregate<TState, TEvent> {
    private final AggregateProjection<TState, TEvent> projection;
    private final String id;
    private int sourceVersion;
    private TState state;
    private final List<TEvent> appliedEvents;

    DefaultMutableAggregate(
            @NotNull final AggregateProjection<TState, TEvent> projection,
            @NotNull final String id,
            final int sourceVersion,
            @NotNull final TState state,
            @NotNull final List<TEvent> events) {
        Preconditions.checkNotNull(projection);
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(state);
        Preconditions.checkNotNull(events);
        this.projection = projection;
        this.id = id;
        this.sourceVersion = sourceVersion;
        this.state = state;
        this.appliedEvents = new ArrayList<>(events);
    }

    /**
     * Creates a new mutable aggregate from the given projection, initialized with the empty
     * value as defined by the projection, no events, and a "not created" version.
     *
     * @param projection The projection used to apply events to the state of the aggregate.
     * @param id         The id of the aggregate.
     * @return A new mutable aggregate with empty state and "not created" version.
     */
    public static <TState, TEvent> DefaultMutableAggregate<TState, TEvent> createNew(
            final AggregateProjection<TState, TEvent> projection,
            final String id) {
        return new DefaultMutableAggregate<>(
                projection,
                id,
                Aggregate.VERSION_NOT_CREATED,
                projection.empty(),
                ImmutableList.of());
    }

    /**
     * Creates a new mutable aggregate state from the given projection, current state and version.
     *
     * @param projection    The projection used to apply events to the state of the aggregate.
     * @param id            The id of the aggregate.
     * @param sourceVersion The current version of the aggregate, in the state provided.
     * @param state         The current state of the aggregate.
     * @return A new mutable aggregate with the provided current state.
     */
    public static <TState, TEvent> DefaultMutableAggregate<TState, TEvent> fromExisting(
            final AggregateProjection<TState, TEvent> projection,
            final String id,
            final int sourceVersion,
            final TState state) {
        return new DefaultMutableAggregate<>(
                projection,
                id,
                sourceVersion,
                state,
                ImmutableList.of());
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

    @Override
    @NotNull
    public TState state() {
        return state;
    }

    @Override
    @NotNull
    public List<TEvent> events() {
        return ImmutableList.copyOf(appliedEvents);
    }

    @Override
    public ImmutableAggregate<TState, TEvent> toImmutable() {
        return new DefaultImmutableAggregate<>(
                projection,
                id,
                sourceVersion,
                state,
                appliedEvents);
    }

    @Override
    public MutableAggregate<TState, TEvent> toMutable() {
        return new DefaultMutableAggregate<>(
                projection,
                id,
                sourceVersion,
                state,
                appliedEvents);
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
    public void rebase(final int version) {
        this.sourceVersion = version;
        this.events().clear();
    }
}
