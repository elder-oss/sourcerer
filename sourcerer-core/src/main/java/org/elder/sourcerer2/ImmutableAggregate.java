package org.elder.sourcerer2;

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

import java.util.stream.Stream;

/**
 * An immutable specialization of Aggregate, implicitly bound to an aggregate projection, that
 * adds supports for updating state by applying events.
 *
 * @param <TState> The type representing state of an aggregate (as relevant to the domain where it
 *                 is used).
 * @param <TEvent> The type of events that can be applied to the aggregate (in the current domain).
 */
public interface ImmutableAggregate<TState, TEvent> extends Aggregate<TState, TEvent> {
    /**
     * Applies a single event to the aggregate state, updating both the encapsulated state, and the
     * local log of events applied to it.
     *
     * @param event The event to apply.
     * @return A new immutable aggregate, representing the aggregate with the event applied,
     * together with the list of applied events.
     */
    @Contract(pure = true)
    @NotNull
    ImmutableAggregate<TState, TEvent> apply(@NotNull TEvent event);

    /**
     * Applies a list of events to the aggregate state, updating both the encapsulated state,
     * and the local log of events applied to it.
     *
     * @param events The events to apply.
     * @return A new immutable aggregate, representing the aggregate with the event applied,
     * together with the list of applied events.
     */
    @Contract(pure = true)
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
    @Contract(pure = true)
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
    @Contract(pure = true)
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
    @Contract(pure = true)
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
    @Contract(pure = true)
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
    @Contract(pure = true)
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
    @Contract(pure = true)
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
    @Contract(pure = true)
    @NotNull
    ImmutableAggregate<TState, TEvent> apply(
            @NotNull UpdateHandlerAggregate<TState, TEvent> handler);

    /**
     * Applies the events derived from an operation handler of the given type. This method is
     * typically used with a Java method reference as argument, allowing for builder/pipe style
     * composition of multiple steps operating on an aggregate.
     *
     * @param handler The handler responsible for generating new events.
     * @return A new immutable aggregate, generated from the current one with the events returned by
     * the handler provided applied on top.
     */
    @Contract(pure = true)
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
    @Contract(pure = true)
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
    @Contract(pure = true)
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
    @Contract(pure = true)
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
    @Contract(pure = true)
    @NotNull <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull ParameterizedUpdateHandlerAggregate<TState, TParam, TEvent> handler,
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
    @Contract(pure = true)
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
    @Contract(pure = true)
    @NotNull <TParam> ImmutableAggregate<TState, TEvent> apply(
            @NotNull ParameterizedPojoUpdateHandlerSingle<TState, TParam, TEvent> handler,
            TParam params);

    /**
     * Applies a function sequentially, once for each value in the provided stream, passing the
     * updated state on to the next invocation in turn and finally returning it. This is effectively
     * a left fold over the provided stream with the current aggregate instance as the initial
     * state.
     *
     * @param handler The handler to invoke one for each parameter in the provided stream.
     * @param params  The parameters to apply to the handler in the order in which they appear in
     *                the stream.
     * @return The result of the last operation being performed in the fold.
     */
    @Contract(pure = true)
    @NotNull <TParam> ImmutableAggregate<TState, TEvent> fold(
            @NotNull ParameterizedUpdateHandler<TState, TParam, TEvent> handler,
            @NotNull Stream<TParam> params);

    /**
     * Applies a function sequentially, once for each value in the provided stream, passing the
     * updated state on to the next invocation in turn and finally returning it. This is effectively
     * a left fold over the provided stream with the current aggregate instance as the initial
     * state.
     *
     * @param handler The handler to invoke one for each parameter in the provided stream.
     * @param params  The parameters to apply to the handler in the order in which they appear in
     *                the stream.
     * @return The result of the last operation being performed in the fold.
     */
    @Contract(pure = true)
    @NotNull <TParam> ImmutableAggregate<TState, TEvent> fold(
            @NotNull ParameterizedUpdateHandlerSingle<TState, TParam, TEvent> handler,
            @NotNull Stream<TParam> params);

    /**
     * Applies a function sequentially, once for each value in the provided stream, passing the
     * updated state on to the next invocation in turn and finally returning it. This is effectively
     * a left fold over the provided stream with the current aggregate instance as the initial
     * state.
     *
     * @param handler The handler to invoke one for each parameter in the provided stream.
     * @param params  The parameters to apply to the handler in the order in which they appear in
     *                the stream.
     * @return The result of the last operation being performed in the fold.
     */
    @Contract(pure = true)
    @NotNull <TParam> ImmutableAggregate<TState, TEvent> fold(
            @NotNull ParameterizedUpdateHandlerAggregate<TState, TParam, TEvent> handler,
            @NotNull Stream<TParam> params);

    /**
     * Applies a function sequentially, once for each value in the provided stream, passing the
     * updated state on to the next invocation in turn and finally returning it. This is effectively
     * a left fold over the provided stream with the current aggregate instance as the initial
     * state.
     *
     * @param handler The handler to invoke one for each parameter in the provided stream.
     * @param params  The parameters to apply to the handler in the order in which they appear in
     *                the stream.
     * @return The result of the last operation being performed in the fold.
     */
    @Contract(pure = true)
    @NotNull <TParam> ImmutableAggregate<TState, TEvent> fold(
            @NotNull ParameterizedPojoUpdateHandler<TState, TParam, TEvent> handler,
            @NotNull Stream<TParam> params);

    /**
     * Applies a function sequentially, once for each value in the provided stream, passing the
     * updated state on to the next invocation in turn and finally returning it. This is effectively
     * a left fold over the provided stream with the current aggregate instance as the initial
     * state.
     *
     * @param handler The handler to invoke one for each parameter in the provided stream.
     * @param params  The parameters to apply to the handler in the order in which they appear in
     *                the stream.
     * @return The result of the last operation being performed in the fold.
     */
    @Contract(pure = true)
    @NotNull <TParam> ImmutableAggregate<TState, TEvent> fold(
            @NotNull ParameterizedPojoUpdateHandlerSingle<TState, TParam, TEvent> handler,
            @NotNull Stream<TParam> params);

    /**
     * Create a new aggregate instance containing the same state as the current one, but with a new
     * version - effectively representing the aggregate in a state after it has been persisted and
     * read back. The new source state will be the current state of the aggregate and the version
     * set to the specified value, and the list of pending events will be reset.
     *
     * @param version The new version of the aggregate.
     * @return A new aggregate representing the aggregate as starting from the current state.
     */
    @Contract(pure = true)
    @NotNull
    ImmutableAggregate<TState, TEvent> rebase(@NotNull StreamVersion version);
}
