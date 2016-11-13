package org.elder.sourcerer.functions;

import com.google.common.collect.ImmutableList;
import org.elder.sourcerer.AggregateState;
import org.elder.sourcerer.OperationHandler;

import java.util.List;
import java.util.Objects;

@FunctionalInterface
public interface ParameterizedConstructorHandlerSingle<TParams, TEvent>
        extends OperationHandler<Object, TParams, TEvent> {
    TEvent executeSingle(TParams params);

    default List<? extends TEvent> execute(
            final AggregateState<Object, TEvent> aggregateState,
            final TParams params) {
        return ImmutableList.of(executeSingle(params));
    }
}
