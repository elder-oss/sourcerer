package org.elder.sourcerer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.elder.sourcerer.functions.AppendHandler;
import org.elder.sourcerer.functions.AppendHandlerSingle;
import org.elder.sourcerer.functions.ParameterizedAppendHandler;
import org.elder.sourcerer.functions.ParameterizedAppendHandlerSingle;
import org.elder.sourcerer.functions.ParameterizedPojoUpdateHandler;
import org.elder.sourcerer.functions.ParameterizedPojoUpdateHandlerSingle;
import org.elder.sourcerer.functions.ParameterizedUpdateHandler;
import org.elder.sourcerer.functions.ParameterizedUpdateHandlerSingle;
import org.elder.sourcerer.functions.ParameterizedUpdateHandlerState;
import org.elder.sourcerer.functions.PojoUpdateHandler;
import org.elder.sourcerer.functions.PojoUpdateHandlerSingle;
import org.elder.sourcerer.functions.UpdateHandler;
import org.elder.sourcerer.functions.UpdateHandlerSingle;
import org.elder.sourcerer.functions.UpdateHandlerState;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Default sourcerer implementation of ImmutableAggregate.
 */
public class DefaultImmutableAggregate<TState, TEvent>
        implements ImmutableAggregate<TState, TEvent> {
    private final AggregateProjection<TState, TEvent> projection;
    private final String id;
    private final int sourceVersion;
    private final TState state;
    private final ImmutableList<TEvent> appliedEvents;

    DefaultImmutableAggregate(
            @NotNull final AggregateProjection<TState, TEvent> projection,
            @NotNull final String id,
            final int sourceVersion,
            @NotNull final TState state,
            @NotNull final List<TEvent> events) {
        Preconditions.checkNotNull(projection);
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(events);
        this.projection = projection;
        this.id = id;
        this.sourceVersion = sourceVersion;
        this.state = state;
        this.appliedEvents = ImmutableList.copyOf(events);
    }

    /**
     * Creates a new immutable aggregate from the given projection, initialized with the empty
     * value as defined by the projection, no events, and a "not created" version.
     *
     * @param projection The projection used to apply events to the state of the aggregate.
     * @param id         The id of the aggregate.
     * @return A new immutable aggregate with empty state and "not created" version.
     */
    public static <TState, TEvent> DefaultImmutableAggregate<TState, TEvent> createNew(
            final AggregateProjection<TState, TEvent> projection,
            final String id) {
        return new DefaultImmutableAggregate<>(
                projection,
                id,
                AggregateState.VERSION_NOT_CREATED,
                projection.empty(),
                ImmutableList.of());
    }

    /**
     * Creates a new immutable aggregate state from the given projection, current state and version.
     *
     * @param projection    The projection used to apply events to the state of the aggregate.
     * @param id            The id of the aggregate.
     * @param sourceVersion The current version of the aggregate, in the state provided.
     * @param state         The current state of the aggregate.
     * @return A new immutable aggregate with the provided current state.
     */
    public static <TState, TEvent> DefaultImmutableAggregate<TState, TEvent> fromExisting(
            final AggregateProjection<TState, TEvent> projection,
            final String id,
            final int sourceVersion,
            final TState state) {
        return new DefaultImmutableAggregate<>(
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
                newState,
                ImmutableList.<TEvent>builder().addAll(appliedEvents).addAll(events).build());
    }

    @NotNull
    @Override
    public ImmutableAggregate<TState, TEvent> apply(
            @NotNull final AppendHandler<TEvent> handler) {
        return this.apply(handler.execute());
    }

    @NotNull
    @Override
    public ImmutableAggregate<TState, TEvent> apply(
            @NotNull final AppendHandlerSingle<TEvent> handler) {
        return this.apply(handler.executeSingle());
    }

    @NotNull
    @Override
    public <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull final ParameterizedAppendHandler<TParam, TEvent> handler,
            final TParam params) {
        return this.apply(handler.execute(params));
    }

    @NotNull
    @Override
    public <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull final ParameterizedAppendHandlerSingle<TParam, TEvent> handler,
            final TParam params) {
        return this.apply(handler.executeSingle(params));
    }

    @NotNull
    @Override
    public ImmutableAggregate<TState, TEvent> apply(
            @NotNull final UpdateHandler<TState, TEvent> handler) {
        return this.apply(handler.execute(this));
    }

    @NotNull
    @Override
    public ImmutableAggregate<TState, TEvent> apply(
            @NotNull final UpdateHandlerSingle<TState, TEvent> handler) {
        return this.apply(handler.executeSingle(this));
    }

    @NotNull
    @Override
    public ImmutableAggregate<TState, TEvent> apply(
            @NotNull final UpdateHandlerState<TState, TEvent> handler) {
        return handler.executeWithState(this).toImmutableAggregate();
    }

    @NotNull
    @Override
    public ImmutableAggregate<TState, TEvent> apply(
            @NotNull final PojoUpdateHandler<TState, TEvent> handler) {
        return this.apply(handler.execute(this.state));
    }

    @NotNull
    @Override
    public ImmutableAggregate<TState, TEvent> apply(
            @NotNull final PojoUpdateHandlerSingle<TState, TEvent> handler) {
        return this.apply(handler.executeSingle(this.state));
    }

    @NotNull
    @Override
    public <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull final ParameterizedUpdateHandler<TState, TParam, TEvent> handler,
            final TParam params) {
        return this.apply(handler.execute(this, params));
    }

    @NotNull
    @Override
    public <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull final ParameterizedUpdateHandlerSingle<TState, TParam, TEvent> handler,
            final TParam params) {
        return this.apply(handler.executeSingle(this, params));
    }

    @NotNull
    @Override
    public <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull final ParameterizedUpdateHandlerState<TState, TParam, TEvent> handler,
            final TParam params) {
        return handler.executeWithState(this, params).toImmutableAggregate();
    }

    @NotNull
    @Override
    public <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull final ParameterizedPojoUpdateHandler<TState, TParam, TEvent> handler,
            final TParam params) {
        return this.apply(handler.execute(this.state, params));
    }

    @NotNull
    @Override
    public <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull final ParameterizedPojoUpdateHandlerSingle<TState, TParam, TEvent> handler,
            final TParam params) {
        return this.apply(handler.executeSingle(this.state, params));
    }

    @NotNull
    @Override
    public ImmutableAggregate<TState, TEvent> rebase(final int version) {
        return new DefaultImmutableAggregate<>(
                projection,
                id,
                version,
                state,
                ImmutableList.of());
    }
}
