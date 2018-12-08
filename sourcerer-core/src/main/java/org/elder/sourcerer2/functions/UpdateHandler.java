package org.elder.sourcerer2.functions;

import com.google.common.base.Preconditions;
import org.elder.sourcerer2.ImmutableAggregate;
import org.elder.sourcerer2.OperationHandler;

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
public interface UpdateHandler<TState, TEvent> extends OperationHandler<TState, Object, TEvent> {
    List<? extends TEvent> execute(ImmutableAggregate<TState, TEvent> aggregate);

    @Override
    default List<? extends TEvent> execute(
            final ImmutableAggregate<TState, TEvent> aggregate,
            final Object params) {
        Preconditions.checkNotNull(aggregate);
        return execute(aggregate);
    }
}
