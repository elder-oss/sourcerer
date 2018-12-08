package org.elder.sourcerer2.functions;

import com.google.common.collect.ImmutableList;
import org.elder.sourcerer2.ImmutableAggregate;
import org.elder.sourcerer2.OperationHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@FunctionalInterface
public interface ParameterizedConstructorHandlerSingle<TParams, TEvent>
        extends OperationHandler<Object, TParams, TEvent> {
    @NotNull
    TEvent executeSingle(TParams params);

    @Override
    default List<? extends TEvent> execute(
            final ImmutableAggregate<Object, TEvent> aggregate,
            final TParams params) {
        return ImmutableList.of(executeSingle(params));
    }
}
