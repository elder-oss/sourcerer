package org.elder.sourcerer2;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.elder.sourcerer2.functions.AppendHandler;
import org.elder.sourcerer2.functions.AppendHandlerSingle;
import org.elder.sourcerer2.functions.ParameterizedAppendHandler;
import org.elder.sourcerer2.functions.ParameterizedAppendHandlerSingle;
import org.elder.sourcerer2.functions.ParameterizedPojoUpdateHandler;
import org.elder.sourcerer2.functions.ParameterizedPojoUpdateHandlerSingle;
import org.elder.sourcerer2.functions.ParameterizedUpdateHandler;
import org.elder.sourcerer2.functions.ParameterizedUpdateHandlerAggregate;
import org.elder.sourcerer2.functions.ParameterizedUpdateHandlerSingle;
import org.elder.sourcerer2.functions.PojoUpdateHandler;
import org.elder.sourcerer2.functions.PojoUpdateHandlerSingle;
import org.elder.sourcerer2.functions.UpdateHandler;
import org.elder.sourcerer2.functions.UpdateHandlerAggregate;
import org.elder.sourcerer2.functions.UpdateHandlerSingle;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * Default sourcerer implementation of ImmutableAggregate.
 */
public class DefaultImmutableAggregate<TState, TEvent>
        implements ImmutableAggregate<TState, TEvent> {
    private final AggregateProjection<TState, TEvent> projection;
    private final StreamId id;
    private final StreamVersion sourceVersion;
    private final TState state;
    private final ImmutableList<TEvent> appliedEvents;

    DefaultImmutableAggregate(
            @NotNull final AggregateProjection<TState, TEvent> projection,
            @NotNull final StreamId id,
            final StreamVersion sourceVersion,
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
            final StreamId id
    ) {
        return new DefaultImmutableAggregate<>(
                projection,
                id,
                Aggregate.VERSION_NOT_CREATED,
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
            final StreamId id,
            final StreamVersion sourceVersion,
            final TState state
    ) {
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
    public StreamId id() {
        return id;
    }

    @Override
    @Contract(pure = true)
    public StreamVersion sourceVersion() {
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
    public ImmutableAggregate<TState, TEvent> toImmutable() {
        return this;
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
            @NotNull final UpdateHandlerAggregate<TState, TEvent> handler) {
        return handler.executeWithAggregate(this).toImmutable();
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
            @NotNull final ParameterizedUpdateHandlerAggregate<TState, TParam, TEvent> handler,
            final TParam params) {
        return handler.executeWithAggregate(this, params).toImmutable();
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
    public <TParam> ImmutableAggregate<TState, TEvent> fold(
            @NotNull final ParameterizedUpdateHandler<TState, TParam, TEvent> handler,
            @NotNull final Stream<TParam> params) {
        return foldLeft(
                params,
                (ImmutableAggregate<TState, TEvent>) this,
                (state, param) -> state.apply(handler, param));
    }

    @NotNull
    @Override
    public <TParam> ImmutableAggregate<TState, TEvent> fold(
            @NotNull final ParameterizedUpdateHandlerSingle<TState, TParam, TEvent> handler,
            @NotNull final Stream<TParam> params) {
        return foldLeft(
                params,
                (ImmutableAggregate<TState, TEvent>) this,
                (state, param) -> state.apply(handler, param));
    }

    @NotNull
    @Override
    public <TParam> ImmutableAggregate<TState, TEvent> fold(
            @NotNull final ParameterizedUpdateHandlerAggregate<TState, TParam, TEvent> handler,
            @NotNull final Stream<TParam> params) {
        return foldLeft(
                params,
                (ImmutableAggregate<TState, TEvent>) this,
                (state, param) -> state.apply(handler, param));
    }

    @NotNull
    @Override
    public <TParam> ImmutableAggregate<TState, TEvent> fold(
            @NotNull final ParameterizedPojoUpdateHandler<TState, TParam, TEvent> handler,
            @NotNull final Stream<TParam> params) {
        return foldLeft(
                params,
                (ImmutableAggregate<TState, TEvent>) this,
                (state, param) -> state.apply(handler, param));
    }

    @NotNull
    @Override
    public <TParam> ImmutableAggregate<TState, TEvent> fold(
            @NotNull final ParameterizedPojoUpdateHandlerSingle<TState, TParam, TEvent> handler,
            @NotNull final Stream<TParam> params) {
        return foldLeft(
                params,
                (ImmutableAggregate<TState, TEvent>) this,
                (state, param) -> state.apply(handler, param));
    }

    @NotNull
    @Override
    public ImmutableAggregate<TState, TEvent> rebase(@NotNull final StreamVersion version) {
        return new DefaultImmutableAggregate<>(
                projection,
                id,
                version,
                state,
                ImmutableList.of());
    }

    private static <T, U> U foldLeft(
            final Stream<T> inputStream,
            final U state,
            final BiFunction<U, T, U> folder) {
        U currentState = state;
        Iterator<T> iterator = inputStream.iterator();
        while (iterator.hasNext()) {
            currentState = folder.apply(currentState, iterator.next());
        }
        return currentState;
    }
}
