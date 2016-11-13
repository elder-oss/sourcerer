package org.elder.sourcerer.functions;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.elder.sourcerer.AggregateState;
import org.elder.sourcerer.OperationHandler;

import java.util.List;

/**
 * An update handler requires access to the current state (and optionally parameters), and performs
 * some update on the aggregate in the form of adding events.
 * <p>
 * Update handler, by default, will require an existing aggregate, and will be atomic, i.e. will
 * succeed only of the version of the aggregate at the time new events are attempted to be appended
 * have the same version as when the aggregate was read.
 */
@FunctionalInterface
public interface ParameterizedUpdateHandlerSingle<TState, TParams, TEvent>
        extends OperationHandler<TState, TParams, TEvent> {
    TEvent executeSingle(AggregateState<TState, TEvent> aggregateState, TParams params);

    default List<? extends TEvent> execute(
            final AggregateState<TState, TEvent> aggregateState,
            final TParams params) {
        Preconditions.checkNotNull(aggregateState);
        return ImmutableList.of(executeSingle(aggregateState, params));
    }
}
