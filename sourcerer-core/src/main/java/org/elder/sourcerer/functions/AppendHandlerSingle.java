package org.elder.sourcerer.functions;

import com.google.common.collect.ImmutableList;
import org.elder.sourcerer.ImmutableAggregate;
import org.elder.sourcerer.OperationHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An append handler is an operations handler capable of appending events to an aggregate, without
 * inspecting its current state. Append handler by default requires that an aggregate with the
 * current id is created - but does not require a particular version.
 */
@FunctionalInterface
public interface AppendHandlerSingle<TEvent>
        extends OperationHandler<Object, Object, TEvent> {
    @NotNull
    TEvent executeSingle();

    @Override
    default List<? extends TEvent> execute(
            final ImmutableAggregate<Object, TEvent> aggregate,
            final Object params) {
        return ImmutableList.of(executeSingle());
    }
}
