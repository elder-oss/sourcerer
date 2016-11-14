package org.elder.sourcerer.functions;

import com.google.common.collect.ImmutableList;
import org.elder.sourcerer.ImmutableAggregate;
import org.elder.sourcerer.OperationHandler;

import java.util.List;

@FunctionalInterface
public interface ConstructorHandlerSingle<TEvent>
        extends OperationHandler<Object, Object, TEvent> {
    TEvent executeSingle();

    @Override
    default List<? extends TEvent> execute(
            final ImmutableAggregate<Object, TEvent> aggregate,
            final Object params) {
        return ImmutableList.of(executeSingle());
    }
}
