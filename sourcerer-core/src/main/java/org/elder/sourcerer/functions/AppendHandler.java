package org.elder.sourcerer.functions;

import org.elder.sourcerer.ImmutableAggregate;
import org.elder.sourcerer.OperationHandler;

import java.util.List;

/**
 * An append handler is an operations handler capable of appending events to an aggregate, without
 * inspecting its current state. Append handler by default requires that an aggregate with the
 * current id is created - but does not require a particular version.
 */
@FunctionalInterface
public interface AppendHandler<TEvent>
        extends OperationHandler<Object, Object, TEvent> {
    List<? extends TEvent> execute();

    default List<? extends TEvent> execute(
            final ImmutableAggregate<Object, TEvent> aggregate,
            final Object params) {
        return execute();
    }
}
