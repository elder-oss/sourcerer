package org.elder.sourcerer.functions;

import com.google.common.collect.ImmutableList;
import org.elder.sourcerer.OperationHandler;

import java.util.List;

@FunctionalInterface
public interface ParameterizedConstructorHandlerSingle<TParams, TEvent>
        extends OperationHandler<Object, TParams, TEvent> {
    TEvent executeSingle(TParams params);

    default List<? extends TEvent> execute(final Object state, final TParams params) {
        return ImmutableList.of(executeSingle(params));
    }
}
