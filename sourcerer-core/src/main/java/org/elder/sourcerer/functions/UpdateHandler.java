package org.elder.sourcerer.functions;

import org.elder.sourcerer.OperationHandler;

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
    List<? extends TEvent> execute(TState state);

    default List<? extends TEvent> execute(final TState state, final Object params) {
        return execute(state);
    }
}
