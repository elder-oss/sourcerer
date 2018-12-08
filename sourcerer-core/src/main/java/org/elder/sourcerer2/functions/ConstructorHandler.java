package org.elder.sourcerer2.functions;

import org.elder.sourcerer2.ImmutableAggregate;
import org.elder.sourcerer2.OperationHandler;

import java.util.List;

@FunctionalInterface
public interface ConstructorHandler<TEvent>
        extends OperationHandler<Object, Object, TEvent> {
    List<? extends TEvent> execute();

    @Override
    default List<? extends TEvent> execute(
            final ImmutableAggregate<Object, TEvent> aggregate,
            final Object params) {
        return execute();
    }
}
