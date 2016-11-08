package org.elder.sourcerer.functions;

import org.elder.sourcerer.OperationHandler;

import java.util.List;

@FunctionalInterface
public interface ParameterizedConstructorHandler<TParams, TEvent>
        extends OperationHandler<Object, TParams, TEvent> {
    List<? extends TEvent> execute(TParams params);

    default List<? extends TEvent> execute(final Object state, final TParams params) {
        return execute(params);
    }
}
