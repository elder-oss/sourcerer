package org.elder.sourcerer.functions;

import com.google.common.base.Preconditions;
import org.elder.sourcerer.AggregateState;
import org.elder.sourcerer.ImmutableAggregate;
import org.elder.sourcerer.OperationHandler;

import java.util.List;

/**
 * An append handler requires access to the current state (and optionally parameters), and performs
 * some append on the aggregate in the form of adding events.
 * <p>
 * Update handler, by default, will require an existing aggregate, and will be atomic, i.e. will
 * succeed only of the version of the aggregate at the time new events are attempted to be appended
 * have the same version as when the aggregate was load.
 */
@FunctionalInterface
public interface ParameterizedUpdateHandlerState<TState, TParams, TEvent>
        extends OperationHandler<TState, TParams, TEvent> {
    AggregateState<TState, TEvent> executeWithState(
            ImmutableAggregate<TState, TEvent> aggregate,
            TParams params);

    default List<? extends TEvent> execute(
            final ImmutableAggregate<TState, TEvent> aggregate,
            final TParams params) {
        Preconditions.checkNotNull(aggregate);
        AggregateState<TState, TEvent>
                updatedState = executeWithState(aggregate, params);
        return updatedState.events();
    }
}
