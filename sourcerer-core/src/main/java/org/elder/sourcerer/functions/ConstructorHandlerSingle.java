package org.elder.sourcerer.functions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ObjectArrays;
import org.elder.sourcerer.AggregateState;
import org.elder.sourcerer.OperationHandler;

import java.util.List;

@FunctionalInterface
public interface ConstructorHandlerSingle<TEvent>
        extends OperationHandler<Object, Object, TEvent> {
    TEvent executeSingle();

    default List<? extends TEvent> execute(
            final AggregateState<Object, TEvent> aggregateState,
            final Object params) {
        return ImmutableList.of(executeSingle());
    }
}
