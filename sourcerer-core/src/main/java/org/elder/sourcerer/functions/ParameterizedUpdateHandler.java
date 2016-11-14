package org.elder.sourcerer.functions;

import org.elder.sourcerer.OperationHandler;

/**
 * An update handler requires access to the current state (and optionally parameters), and performs
 * some append on the aggregate in the form of adding events.
 * <p>
 * Update handler, by default, will require an existing aggregate, and will be atomic, i.e. will
 * succeed only of the version of the aggregate at the time new events are attempted to be appended
 * have the same version as when the aggregate was load.
 */
@FunctionalInterface
public interface ParameterizedUpdateHandler<TState, TParams, TEvent>
        extends OperationHandler<TState, TParams, TEvent> {
}
