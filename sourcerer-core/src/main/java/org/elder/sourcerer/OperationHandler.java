package org.elder.sourcerer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Functional interfaces for handler functions providing the logic for Operations.
 * org.elder.sourcerer.functions contain specializations of this interface matching a number of
 * handler signatures for various types of operations. {@link Operation} type.
 *
 * @param <TState>  The type of aggregates that this operation acts on.
 * @param <TParams> The type of parameters that this operation accepts.
 * @param <TEvent>  The type of event that this operation produces.
 */
public interface OperationHandler<TState, TParams, TEvent> {
    /**
     * Execute the operation. An operation should return a list of events to be appended to an
     * aggregate if the command it represents is valid in the current state of the aggregate and
     * given the provided parameters.
     * <p>
     * An operation may make use of other state and external resources in order to determine whether
     * it is valid in the current state, and if so what the resulting events should be (so does not
     * have to be a pure function). However, an operation should not attempt to modify any state
     * related to the aggregate it operates on other than in the form of returning events, and does
     * need to be written to support retries.
     *
     * @param aggregate The aggregate that the operation is executed on.
     * @param params    The arguments applied to the operation.
     * @return A list of events to be applied to the aggregate to update its state, may be empty if
     * the operation is a no-op, must not be null,
     */
    @NotNull
    List<? extends TEvent> execute(
            @Nullable ImmutableAggregate<TState, TEvent> aggregate,
            @Nullable TParams params);
}
