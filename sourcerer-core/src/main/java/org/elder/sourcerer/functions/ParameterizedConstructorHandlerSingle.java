package org.elder.sourcerer.functions;

import com.google.common.collect.ImmutableList;
import org.elder.sourcerer.ImmutableAggregate;
import org.elder.sourcerer.OperationHandler;

import java.util.List;

@FunctionalInterface
public interface ParameterizedConstructorHandlerSingle<TParams, TEvent>
        extends OperationHandler<Object, TParams, TEvent> {
    TEvent executeSingle(TParams params);

    @Override
    default List<? extends TEvent> execute(
            final ImmutableAggregate<Object, TEvent> aggregate,
            final TParams params) {
        return ImmutableList.of(executeSingle(params));
    }
}
