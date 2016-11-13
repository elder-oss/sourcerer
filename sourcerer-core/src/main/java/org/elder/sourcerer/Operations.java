package org.elder.sourcerer;

import org.elder.sourcerer.functions.AppendHandler;
import org.elder.sourcerer.functions.AppendHandlerSingle;
import org.elder.sourcerer.functions.ConstructorHandler;
import org.elder.sourcerer.functions.ConstructorHandlerSingle;
import org.elder.sourcerer.functions.ParameterizedAppendHandler;
import org.elder.sourcerer.functions.ParameterizedAppendHandlerSingle;
import org.elder.sourcerer.functions.ParameterizedConstructorHandler;
import org.elder.sourcerer.functions.ParameterizedConstructorHandlerSingle;
import org.elder.sourcerer.functions.ParameterizedPojoUpdateHandler;
import org.elder.sourcerer.functions.ParameterizedPojoUpdateHandlerSingle;
import org.elder.sourcerer.functions.ParameterizedUpdateHandler;
import org.elder.sourcerer.functions.ParameterizedUpdateHandlerSingle;
import org.elder.sourcerer.functions.PojoUpdateHandler;
import org.elder.sourcerer.functions.PojoUpdateHandlerSingle;
import org.elder.sourcerer.functions.UpdateHandler;
import org.elder.sourcerer.functions.UpdateHandlerSingle;

/**
 * Utility functions for building functions from various method types.
 */
public final class Operations {
    private Operations() {
    }

    public static <TEvent> Operation<Object, Object, TEvent> constructorOf(
            final ConstructorHandler<TEvent> handler) {
        return new OperationHandlerOperation<>(handler, false, false, ExpectedVersion.notCreated());
    }

    public static <TEvent> Operation<Object, Object, TEvent> constructorOf(
            final ConstructorHandlerSingle<TEvent> handler) {
        return new OperationHandlerOperation<>(handler, false, false, ExpectedVersion.notCreated());
    }

    public static <TParams, TEvent> Operation<Object, TParams, TEvent> constructorOf(
            final ParameterizedConstructorHandler<TParams, TEvent> handler) {
        return new OperationHandlerOperation<>(handler, false, false, ExpectedVersion.notCreated());
    }

    public static <TParams, TEvent> Operation<Object, TParams, TEvent> constructorOf(
            final ParameterizedConstructorHandlerSingle<TParams, TEvent> handler) {
        return new OperationHandlerOperation<>(handler, false, false, ExpectedVersion.notCreated());
    }

    public static <TEvent> Operation<Object, Object, TEvent> appendOf(
            final AppendHandler<TEvent> handler) {
        return new OperationHandlerOperation<>(handler, false, false);
    }

    public static <TEvent> Operation<Object, Object, TEvent> appendOf(
            final AppendHandlerSingle<TEvent> handler) {
        return new OperationHandlerOperation<>(handler, false, false);
    }

    public static <TParams, TEvent> Operation<Object, TParams, TEvent> appendOf(
            final ParameterizedAppendHandler<TParams, TEvent> handler) {
        return new OperationHandlerOperation<>(handler, false, true);
    }

    public static <TParams, TEvent> Operation<Object, TParams, TEvent> appendOf(
            final ParameterizedAppendHandlerSingle<TParams, TEvent> handler) {
        return new OperationHandlerOperation<>(handler, false, true);
    }

    /**
     * Creates an update operation, requiring an existing aggregate.
     *
     * @param handler The handler to invoke with the current state to apply updates.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TEvent> Operation<TState, Object, TEvent> updateOf(
            final UpdateHandler<TState, TEvent> handler) {
        return updateOf(handler, false);
    }

    /**
     * Creates an update operation, requiring an existing aggregate.
     *
     * @param handler The handler to invoke with the current state to apply updates.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TEvent> Operation<TState, Object, TEvent> updateOf(
            final UpdateHandlerSingle<TState, TEvent> handler) {
        return updateOf(handler, false);
    }

    /**
     * Creates an update operation, requiring an existing aggregate.
     *
     * @param handler The handler to invoke with the current state to apply updates.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TParams, TEvent> Operation<TState, TParams, TEvent> updateOf(
            final ParameterizedUpdateHandler<TState, TParams, TEvent> handler) {
        return updateOf(handler, false);
    }

    /**
     * Creates an update operation, requiring an existing aggregate.
     *
     * @param handler The handler to invoke with the current state to apply updates.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TParams, TEvent> Operation<TState, TParams, TEvent> updateOf(
            final ParameterizedUpdateHandlerSingle<TState, TParams, TEvent> handler) {
        return updateOf(handler, false);
    }

    /**
     * Creates an update operation, optionally requiring an existing aggregate.
     *
     * @param handler    The handler to invoke with the current state to apply updates.
     * @param autoCreate If true, the handler will be invoked with a null state in the case where no
     *                   current aggregate exists. If false, the command will fail without the
     *                   handler being invoked if the aggregate is not already present.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TEvent> Operation<TState, Object, TEvent> updateOf(
            final UpdateHandler<TState, TEvent> handler,
            final boolean autoCreate) {
        ExpectedVersion expectedVersion = autoCreate
                ? ExpectedVersion.any()
                : ExpectedVersion.anyExisting();
        return new OperationHandlerOperation<>(
                handler,
                true,
                false,
                expectedVersion,
                true);
    }

    /**
     * Creates an update operation, optionally requiring an existing aggregate.
     *
     * @param handler    The handler to invoke with the current state to apply updates.
     * @param autoCreate If true, the handler will be invoked with a null state in the case where no
     *                   current aggregate exists. If false, the command will fail without the
     *                   handler being invoked if the aggregate is not already present.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TEvent> Operation<TState, Object, TEvent> updateOf(
            final UpdateHandlerSingle<TState, TEvent> handler,
            final boolean autoCreate) {
        ExpectedVersion expectedVersion = autoCreate
                ? ExpectedVersion.any()
                : ExpectedVersion.anyExisting();
        return new OperationHandlerOperation<>(
                handler,
                true,
                false,
                expectedVersion,
                true);
    }

    /**
     * Creates an update operation, optionally requiring an existing aggregate.
     *
     * @param handler    The handler to invoke with the current state to apply updates.
     * @param autoCreate If true, the handler will be invoked with a null state in the case where no
     *                   current aggregate exists. If false, the command will fail without the
     *                   handler being invoked if the aggregate is not already present.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TParams, TEvent> Operation<TState, TParams, TEvent> updateOf(
            final ParameterizedUpdateHandler<TState, TParams, TEvent> handler,
            final boolean autoCreate) {
        ExpectedVersion expectedVersion = autoCreate
                ? ExpectedVersion.any()
                : ExpectedVersion.anyExisting();
        return new OperationHandlerOperation<>(
                handler,
                true,
                true,
                expectedVersion,
                true);
    }

    /**
     * Creates an update operation, optionally requiring an existing aggregate.
     *
     * @param handler    The handler to invoke with the current state to apply updates.
     * @param autoCreate If true, the handler will be invoked with a null state in the case where no
     *                   current aggregate exists. If false, the command will fail without the
     *                   handler being invoked if the aggregate is not already present.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TParams, TEvent> Operation<TState, TParams, TEvent> updateOf(
            final ParameterizedUpdateHandlerSingle<TState, TParams, TEvent> handler,
            final boolean autoCreate) {
        ExpectedVersion expectedVersion = autoCreate
                ? ExpectedVersion.any()
                : ExpectedVersion.anyExisting();
        return new OperationHandlerOperation<>(
                handler,
                true,
                true,
                expectedVersion,
                true);
    }

    /**
     * Creates an update operation, requiring an existing aggregate.
     *
     * @param handler The handler to invoke with the current state to apply updates.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TEvent> Operation<TState, Object, TEvent> updateOf(
            final PojoUpdateHandler<TState, TEvent> handler) {
        return updateOf(handler, false);
    }

    /**
     * Creates an update operation, requiring an existing aggregate.
     *
     * @param handler The handler to invoke with the current state to apply updates.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TEvent> Operation<TState, Object, TEvent> updateOf(
            final PojoUpdateHandlerSingle<TState, TEvent> handler) {
        return updateOf(handler, false);
    }

    /**
     * Creates an update operation, requiring an existing aggregate.
     *
     * @param handler The handler to invoke with the current state to apply updates.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TParams, TEvent> Operation<TState, TParams, TEvent> updateOf(
            final ParameterizedPojoUpdateHandler<TState, TParams, TEvent> handler) {
        return updateOf(handler, false);
    }

    /**
     * Creates an update operation, requiring an existing aggregate.
     *
     * @param handler The handler to invoke with the current state to apply updates.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TParams, TEvent> Operation<TState, TParams, TEvent> updateOf(
            final ParameterizedPojoUpdateHandlerSingle<TState, TParams, TEvent> handler) {
        return updateOf(handler, false);
    }

    /**
     * Creates an update operation, optionally requiring an existing aggregate.
     *
     * @param handler    The handler to invoke with the current state to apply updates.
     * @param autoCreate If true, the handler will be invoked with a null state in the case where no
     *                   current aggregate exists. If false, the command will fail without the
     *                   handler being invoked if the aggregate is not already present.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TEvent> Operation<TState, Object, TEvent> updateOf(
            final PojoUpdateHandler<TState, TEvent> handler,
            final boolean autoCreate) {
        ExpectedVersion expectedVersion = autoCreate
                ? ExpectedVersion.any()
                : ExpectedVersion.anyExisting();
        return new OperationHandlerOperation<>(
                handler,
                true,
                false,
                expectedVersion,
                true);
    }

    /**
     * Creates an update operation, optionally requiring an existing aggregate.
     *
     * @param handler    The handler to invoke with the current state to apply updates.
     * @param autoCreate If true, the handler will be invoked with a null state in the case where no
     *                   current aggregate exists. If false, the command will fail without the
     *                   handler being invoked if the aggregate is not already present.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TEvent> Operation<TState, Object, TEvent> updateOf(
            final PojoUpdateHandlerSingle<TState, TEvent> handler,
            final boolean autoCreate) {
        ExpectedVersion expectedVersion = autoCreate
                ? ExpectedVersion.any()
                : ExpectedVersion.anyExisting();
        return new OperationHandlerOperation<>(
                handler,
                true,
                false,
                expectedVersion,
                true);
    }

    /**
     * Creates an update operation, optionally requiring an existing aggregate.
     *
     * @param handler    The handler to invoke with the current state to apply updates.
     * @param autoCreate If true, the handler will be invoked with a null state in the case where no
     *                   current aggregate exists. If false, the command will fail without the
     *                   handler being invoked if the aggregate is not already present.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TParams, TEvent> Operation<TState, TParams, TEvent> updateOf(
            final ParameterizedPojoUpdateHandler<TState, TParams, TEvent> handler,
            final boolean autoCreate) {
        ExpectedVersion expectedVersion = autoCreate
                ? ExpectedVersion.any()
                : ExpectedVersion.anyExisting();
        return new OperationHandlerOperation<>(
                handler,
                true,
                true,
                expectedVersion,
                true);
    }

    /**
     * Creates an update operation, optionally requiring an existing aggregate.
     *
     * @param handler    The handler to invoke with the current state to apply updates.
     * @param autoCreate If true, the handler will be invoked with a null state in the case where no
     *                   current aggregate exists. If false, the command will fail without the
     *                   handler being invoked if the aggregate is not already present.
     * @return An operation representing the supplied logic, with metadata.
     */
    public static <TState, TParams, TEvent> Operation<TState, TParams, TEvent> updateOf(
            final ParameterizedPojoUpdateHandlerSingle<TState, TParams, TEvent> handler,
            final boolean autoCreate) {
        ExpectedVersion expectedVersion = autoCreate
                ? ExpectedVersion.any()
                : ExpectedVersion.anyExisting();
        return new OperationHandlerOperation<>(
                handler,
                true,
                true,
                expectedVersion,
                true);
    }
}
