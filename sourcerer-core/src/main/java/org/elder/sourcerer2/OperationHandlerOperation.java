package org.elder.sourcerer2;

import java.util.List;

/**
 * Implementation of Operation in terms of an OperationHandler and parameters.
 */
public class OperationHandlerOperation<TState, TParams, TEvent>
        implements Operation<TState, TParams, TEvent> {
    private final OperationHandler<TState, TParams, TEvent> handler;
    private final boolean requiresState;
    private final boolean requiresArguments;
    private final ExpectedVersion expectedVersion;
    private boolean atomic;

    public OperationHandlerOperation(
            final OperationHandler<TState, TParams, TEvent> handler,
            final boolean requiresState,
            final boolean requiresArguments) {
        this(handler, requiresState, requiresArguments, ExpectedVersion.any(), false);
    }

    public OperationHandlerOperation(
            final OperationHandler<TState, TParams, TEvent> handler,
            final boolean requiresState,
            final boolean requiresArguments,
            final ExpectedVersion expectedVersion) {
        this(handler, requiresState, requiresArguments, expectedVersion, false);
    }

    public OperationHandlerOperation(
            final OperationHandler<TState, TParams, TEvent> handler,
            final boolean requiresState,
            final boolean requiresArguments,
            final ExpectedVersion expectedVersion,
            final boolean atomic) {
        this.handler = handler;
        this.requiresState = requiresState;
        this.requiresArguments = requiresArguments;
        this.expectedVersion = expectedVersion;
        this.atomic = atomic;
    }

    @Override
    public List<? extends TEvent> execute(
            final ImmutableAggregate<TState, TEvent> aggregate,
            final TParams params) {
        return handler.execute(aggregate, params);
    }

    @Override
    public boolean requiresState() {
        return requiresState;
    }

    @Override
    public boolean requiresArguments() {
        return requiresArguments;
    }

    @Override
    public ExpectedVersion expectedVersion() {
        return expectedVersion;
    }

    @Override
    public boolean atomic() {
        return atomic;
    }

    public OperationHandler<TState, TParams, TEvent> handler() {
        return handler;
    }
}
