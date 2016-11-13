package org.elder.sourcerer.functions;

import org.elder.sourcerer.AggregateState;
import org.elder.sourcerer.OperationHandler;

import java.util.List;

@FunctionalInterface
public interface ConstructorHandler<TEvent>
        extends OperationHandler<Object, Object, TEvent> {
    List<? extends TEvent> execute();

    default List<? extends TEvent> execute(
            final AggregateState<Object, TEvent> aggregateState,
            final Object params) {
        return execute();
    }
}
