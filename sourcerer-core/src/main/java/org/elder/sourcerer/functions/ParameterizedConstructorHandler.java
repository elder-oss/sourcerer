package org.elder.sourcerer.functions;

import org.elder.sourcerer.AggregateState;
import org.elder.sourcerer.OperationHandler;

import java.util.List;

@FunctionalInterface
public interface ParameterizedConstructorHandler<TParams, TEvent>
        extends OperationHandler<Object, TParams, TEvent> {
    List<? extends TEvent> execute(TParams params);

    default List<? extends TEvent> execute(
            final AggregateState<Object, TEvent> aggregateState,
            final TParams params) {
        return execute(params);
    }
}
