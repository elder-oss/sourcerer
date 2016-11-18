package org.elder.sourcerer.functions;

import org.elder.sourcerer.ImmutableAggregate;
import org.elder.sourcerer.OperationHandler;

import java.util.List;

@FunctionalInterface
public interface ParameterizedConstructorHandler<TParams, TEvent>
        extends OperationHandler<Object, TParams, TEvent> {
    List<? extends TEvent> execute(TParams params);

    @Override
    default List<? extends TEvent> execute(
            final ImmutableAggregate<Object, TEvent> aggregate,
            final TParams params) {
        return execute(params);
    }
}
