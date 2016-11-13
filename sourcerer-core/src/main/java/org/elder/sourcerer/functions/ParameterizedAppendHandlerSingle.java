package org.elder.sourcerer.functions;

import com.google.common.collect.ImmutableList;
import org.elder.sourcerer.AggregateState;
import org.elder.sourcerer.OperationHandler;

import java.util.List;

/**
 * An append handler is an operations handler capable of appending events to an aggregate, without
 * inspecting its current state. Append handler by default requires that an aggregate with the
 * current id is created - but does not require a particular version.
 */
@FunctionalInterface
public interface ParameterizedAppendHandlerSingle<TParams, TEvent>
        extends OperationHandler<Object, TParams, TEvent> {
    TEvent executeSingle(TParams params);

    default List<? extends TEvent> execute(
            final AggregateState<Object, TEvent> aggregateState,
            final TParams params) {
        return ImmutableList.of(executeSingle(params));
    }
}
