package org.elder.sourcerer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An operation provides the business logic for executing a command given a state of an aggregate
 * and parameters. Rather than implementing this interface directly, clients would normally
 * implement one of the functional interfaces in org.elder.sourcerer.functions, and use {@link
 * Operations} to convert them to this type.
 *
 * @param <TState>  The type of aggregates that the command operates on. A command intended to be
 *                  used with an AggregateRepository should have a TState type that matches, or is a
 *                  base class of, the TState type for the repository.
 * @param <TParams> The type of parameters that the command requires. If no parameters are used,
 *                  specify Void.
 * @param <TEvent>  The type of events that this command produces. A command intended to be used
 *                  with an AggregateRepository should have a TEvent type that matches, or is a
 *                  subclass of, the event type specified for the repository.
 */
public interface Operation<TState, TParams, TEvent> {
    /**
     * Execute the operation. An operation should return a list of events to be appended to an
     * aggregate if the command it represents is valid in the current state of the aggregate and
     * given the provided parameters.
     * <p>
     * An operation may make use of other state and external resources in order to determine whether
     * it is valid in the current state, and if so what the resulting events should be (so does not
     * have to be a pure function). However, an operation should not attempt to modify any state
     * related to the aggregate it operates on other than in the form of returning events, and does
     * need to be written to support retries.
     *
     * @param aggregate The aggregate that the operation is executed on. A null value indicates that
     *                  no aggregate has been retrieved and the current state is unknown. If the
     *                  aggregate is new (did not exist at the time it was loaded), this will be
     *                  identified by the source version set to -1. The state will be the default
     *                  empty value according to the projection and never null.
     * @param params    The arguments applied to the operation.
     * @return A list of events to be applied to the aggregate to update its state, may be empty if
     * the operation is a no-op, must not be null,
     */
    @NotNull List<? extends TEvent> execute(
            @Nullable ImmutableAggregate<TState, TEvent> aggregate,
            @Nullable TParams params);

    /**
     * True if this operation requires state to execute, false if not. An operation with
     * requiresState as false will be given null as its state argument (if applicable), regardless
     * of the current state of the aggregate.
     */
    boolean requiresState();

    /**
     * True if this operation requires arguments to execute, false if not. An operation with
     * requiresArguments as false will be given null as its params argument (if applicable)
     * regardless of any arguments provided.
     */
    boolean requiresArguments();

    /**
     * Gets the version of a stream expected by the operation. This is normally either
     * ExpectedVersion.anyExisting(), or ExpectedVersion.notCreated() for updating vs creating
     * functions.
     * <p>
     * The expected version requirement will be merged with that set on the command created from
     * this operation, if one is provided.
     */
    ExpectedVersion expectedVersion();

    /**
     * True if the operation is atomic, i.e. would fail if there were concurrent modifications to
     * the aggregate in between it being load, and new events being added. This will set the default
     * value for the command when created from an operation, which can be overwritten by explicitly
     * setting atomic to true or false on the command.
     */
    boolean atomic();
}
