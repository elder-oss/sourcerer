package org.elder.sourcerer.functions;

import com.google.common.collect.ImmutableList;
import org.elder.sourcerer.OperationHandler;

import java.util.List;

/**
 * An append handler is an operations handler capable of appending events to an aggregate, without
 * inspecting its current state. Append handler by default requires that an aggregate with the
 * current id is created - but does not require a particular version.
 */
@FunctionalInterface
public interface AppendHandlerSingle<TEvent>
        extends OperationHandler<Object, Object, TEvent> {
    TEvent executeSingle();

    default List<? extends TEvent> execute(final Object state, final Object params) {
        return ImmutableList.of(executeSingle());
    }
}
