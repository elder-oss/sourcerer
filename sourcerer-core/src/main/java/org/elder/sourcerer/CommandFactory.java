package org.elder.sourcerer;

/**
 * The command factory is responsible for creating commands from operations and operation handlers.
 * Command services normally only need this dependency in order to
 *
 * @param <TState> The types of aggregates that this command factory produces commands for.
 * @param <TEvent> The type of events produced by the command.
 */
public interface CommandFactory<TState, TEvent> {
    /**
     * Creates a new command bound to this repository for a given operation.
     *
     * @param operation The operation (describing the business logic associated with a command) to
     *                  create a command from.
     * @param <TParams> The type of parameters required by the operation. If no parameters are
     *                  required, use Void.
     * @return A Command instance that can be further configured and ultimated run to create or
     * update a single aggregate.
     */
    <TParams> Command<TState, TParams, TEvent> fromOperation(
            Operation<? super TState, ? super TParams, ? extends TEvent> operation);
}
