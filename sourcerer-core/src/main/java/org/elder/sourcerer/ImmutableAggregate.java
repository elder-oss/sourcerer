package org.elder.sourcerer;

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
import org.jetbrains.annotations.NotNull;

/**
 * An immutable specialization of AggregateState, implicitly bound to an aggregate projection, that
 * adds supports for updating state by applying events.
 *
 * @param <TState> The type representing state of an aggregate (as relevant to the domain where it
 *                 is used).
 * @param <TEvent> The type of events that can be applied to the aggregate (in the current domain).
 */
public interface ImmutableAggregate<TState, TEvent> extends AggregateState<TState, TEvent> {
    /**
     * Applies a single event to the aggregate state, updating both the encapsulated state, and the
     * local log of events applied to it.
     *
     * @param event The event to apply.
     * @return A new immutable aggregate state, representing the state of the aggregate with the
     * event applied, together with the list of applied events.
     */
    @NotNull
    ImmutableAggregate<TState, TEvent> apply(@NotNull TEvent event);

    /**
     * Applies a list of events to the aggregate state, updating both the encapsulated state,
     * and the local log of events applied to it.
     *
     * @param events The events to apply.
     * @return A new immutable aggregate state, representing the state of the aggregate with the
     * event applied, together with the list of applied events.
     */
    @NotNull
    ImmutableAggregate<TState, TEvent> apply(@NotNull Iterable<? extends TEvent> events);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @NotNull
    ImmutableAggregate<TState, TEvent> apply(@NotNull AppendHandler<TEvent> handler);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @NotNull
    ImmutableAggregate<TState, TEvent> apply(@NotNull AppendHandlerSingle<TEvent> handler);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @param params  Parameters to pass on to the handler when invoked.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @NotNull <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull ParameterizedAppendHandler<TParam, TEvent> handler,
            TParam params);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @param params  Parameters to pass on to the handler when invoked.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @NotNull <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull ParameterizedAppendHandlerSingle<TParam, TEvent> handler,
            TParam params);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @NotNull
    ImmutableAggregate<TState, TEvent> apply(
            @NotNull UpdateHandler<TState, TEvent> handler);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @NotNull
    ImmutableAggregate<TState, TEvent> apply(
            @NotNull UpdateHandlerSingle<TState, TEvent> handler);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @NotNull
    ImmutableAggregate<TState, TEvent> apply(
            @NotNull UpdateHandlerState<TState, TEvent> handler);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @NotNull
    ImmutableAggregate<TState, TEvent> apply(
            @NotNull PojoUpdateHandler<TState, TEvent> handler);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @NotNull
    ImmutableAggregate<TState, TEvent> apply(
            @NotNull PojoUpdateHandlerSingle<TState, TEvent> handler);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @param params  Parameters to pass on to the handler when invoked.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @NotNull <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull ParameterizedUpdateHandler<TState, TParam, TEvent> handler,
            TParam params);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @param params  Parameters to pass on to the handler when invoked.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @NotNull <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull ParameterizedUpdateHandlerSingle<TState, TParam, TEvent> handler,
            TParam params);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @param params  Parameters to pass on to the handler when invoked.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @NotNull <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull ParameterizedUpdateHandlerState<TState, TParam, TEvent> handler,
            TParam params);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @param params  Parameters to pass on to the handler when invoked.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @NotNull <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull ParameterizedPojoUpdateHandler<TState, TParam, TEvent> handler,
            TParam params);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @param params  Parameters to pass on to the handler when invoked.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @NotNull <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull ParameterizedPojoUpdateHandlerSingle<TState, TParam, TEvent> handler,
            TParam params);

    /**
     * Create a new aggregate instance containing the same state as the current one, but with a new
     * version - effectively representing the aggregate in a state after it has been persisted and
     * read back. The new source state will be the current state of the aggregate and the version
     * set to the specified value, and the list of pending events will be reset.
     *
     * @param version The new version of the aggregate.
     * @return A new aggregate representing the aggregate as starting from the current state.
     */
    @NotNull
    ImmutableAggregate<TState, TEvent> rebase(int version);
}
