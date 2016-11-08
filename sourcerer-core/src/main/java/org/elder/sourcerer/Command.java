package org.elder.sourcerer;

import java.util.Map;

/**
 * A Command represents an operation performed on a single aggregate to update its state. Commands,
 * unlike events, can be rejected if the command is not applicable to the aggregate in its current
 * state, or the arguments passed the the command do not apply.
 * <p>
 * An instance of Command is mutable, but once configured, can be executed multiple times or delayed
 * for later execution (to support handling intermittent errors and circuit breaker patterns).
 * <p>
 * A command is at a minimum bound to a specific aggregate and operation. The Operation represents
 * the business logic performed on the aggregate to update its state. In addition, a command may
 * have additional components such as validators used to check that the given arguments are valid,
 * and constraints on the current version on an aggregate for optimistic concurrency.
 *
 * @param <TState>  The type of aggregates that the command operates on. When used with an
 *                  AggregateRepository, this should match, or be a base class of the TState type of
 *                  the repository.
 * @param <TParams> The type of parameters that the command accepts. If the commands does not accept
 *                  any parameters, use Void.
 * @param <TEvent>  The type of event that the command produces to update the aggregate if
 *                  successful. When used with an AggregateRepository, this should match, or be a
 *                  subclass of the TEvent type of the repository.
 */
public interface Command<TState, TParams, TEvent> {
    /**
     * Specifies the aggregate id of the aggregate to operate on.
     *
     * @param aggregateId The string id used to identify the aggregate to operate on.
     * @return The command instance that the method was invoked on, for method chaining.
     */
    Command<TState, TParams, TEvent> setAggregateId(String aggregateId);

    /**
     * Sets the arguments passed to the operation behind this command as the command is executed.
     *
     * @param arguments The arguments to pass to the underlying operation as the command is
     *                  executed. Arguments should not be modified once passed to a command, and are
     *                  recommended to be immutable to ensure this.
     * @return The command instance that the method was invoked on, for method chaining.
     */
    Command<TState, TParams, TEvent> setArguments(TParams arguments);

    /**
     * Set the expected version of the aggregate. When an expected version is specified, the command
     * will fail with a {@link org.elder.sourcerer.exceptions.UnexpectedVersionException} if the
     * current version of the aggregate at the time the command is executed differs from the one
     * specified. To also ensure that no modifications have taken place concurrently as the command
     * is executing, it is recommended that setAtomic(true) is used.
     *
     * @param version The version that the aggregate is expected to be in when the command is
     *                executed. Note that the version may specify that the aggregate does not
     *                currently exist, as well as a specific version.
     * @return The command instance that the method was invoked on, for method chaining.
     */
    Command<TState, TParams, TEvent> setExpectedVersion(ExpectedVersion version);

    /**
     * Sets whether or not the command is atomic. An atomic command requires the version of the
     * aggregate when read to be the same as when updated, i.e. would fail if there were any other
     * concurrent modifications during the execution of the command. When true, concurrent
     * modifications of the aggregate may lead to an
     * {@link org.elder.sourcerer.exceptions.UnexpectedVersionException}
     * being thrown when the command is executed.
     *
     * @param atomic True if the command is atomic, false to permit concurrent modifications of the
     *               aggregate when the command is run.
     * @return The command instance that the method was invoked on, for method chaining.
     */
    Command<TState, TParams, TEvent> setAtomic(boolean atomic);

    /**
     * If set to true, operations that are attempted on an existing stream will be silently ignored.
     * This can be useful for idempotent creates caused by retries, but case must be taken to ensure
     * that the same intended parameters are used every time the same aggregate id is specified.
     *
     * @param idempotentCreate True to silently ignore the operation if a stream already exists.
     * @return The command instance that the method was invoked on, for method chaining.
     */
    Command<TState, TParams, TEvent> setIdempotentCreate(boolean idempotentCreate);

    /**
     * Add outright metadata to attach to each event emitted by the command when executed (if any).
     * Values for keys added later will take priority over those added earlier, and any values
     * produced by a metadata decorator will overwrite those added explicitly.
     * @param metadata Metadata in the form of key value pairs to add to emitted events (if any).
     * @return The command instance that the method was invoked on, for method chaining.
     */
    Command<TState, TParams, TEvent> addMetadata(Map<String, String> metadata);

    /**
     * Sets a metadata decorators that will be called after an operation has been completed, but
     * before events are persistent. The metadata decorators can be used to add data orthogonal to
     * the event itself, i.e. authentication, data about the server processing the event, etc.
     * <p>
     * If multiple metadata decorators are provided, they will be called in the order in which they
     * were added, with later annotators overwriting values from the previous when more than one
     * contains a value for a given key.
     *
     * @param metadataDecorator The annotator to invoke to get metadata for events.
     * @return The command instance that the method was invoked on, for method chaining.
     */
    Command<TState, TParams, TEvent> addMetadataDecorator(MetadataDecorator metadataDecorator);

    /**
     * Validates the command as constructed so far for completeness and consistency. This method
     * will fail with an exception if the command is not configured correctly, e.g. expects
     * arguments but notCreated have been given, or does not have an aggregate id specified.
     *
     * @return The command instance that the method was invoked on, for method chaining.
     */
    Command<TState, TParams, TEvent> validate();

    /**
     * Runs the command. The exact logic for running a command is implementation specific, but is
     * semantically equivalent to constructing the aggregate state from events, passing it to the
     * underlying operation, and appending the events generated by the operation to the stream of
     * events for the aggregate.
     * <p>
     * The command implementation may add additional steps, such as validation of parameters,
     * throttling, and retries for intermittent failures.
     *
     * @return A CommandResult describing the state of the aggregate after successful execution of
     * the command, including the current version and new events applied to the aggregate.
     */
    CommandResult<TEvent> run();
}
