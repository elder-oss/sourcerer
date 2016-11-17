package org.elder.sourcerer.functions;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.elder.sourcerer.ImmutableAggregate;
import org.elder.sourcerer.OperationHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An update handler requires access to the current state (and optionally parameters), and performs
 * some update on the aggregate in the form of adding events.
 * <p>
 * Update handler, by default, will require an existing aggregate, and will be atomic, i.e. will
 * succeed only of the version of the aggregate at the time new events are attempted to be appended
 * have the same version as when the aggregate was read.
 */
@FunctionalInterface
public interface ParameterizedUpdateHandlerSingle<TState, TParams, TEvent>
        extends OperationHandler<TState, TParams, TEvent> {
    @NotNull
    TEvent executeSingle(ImmutableAggregate<TState, TEvent> aggregate, TParams params);

    @Override
    default List<? extends TEvent> execute(
            final ImmutableAggregate<TState, TEvent> aggregate,
            final TParams params) {
        Preconditions.checkNotNull(aggregate);
        return ImmutableList.of(executeSingle(aggregate, params));
    }
}
