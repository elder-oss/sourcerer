package org.elder.sourcerer.functions;

import org.elder.sourcerer.ImmutableAggregate;
import org.elder.sourcerer.OperationHandler;

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
