package org.elder.sourcerer2;

import com.google.common.collect.ImmutableList;
import org.elder.sourcerer2.utils.RetryPolicy;

import java.util.List;

public class DefaultCommandFactory<TState, TEvent> implements CommandFactory<TState, TEvent> {
    private final AggregateRepository<TState, TEvent> repository;
    private final List<CommandPostProcessor> postProcessors;
    private final RetryPolicy retryPolicy;

    public DefaultCommandFactory(final AggregateRepository<TState, TEvent> repository) {
        this(repository, (List<CommandPostProcessor>) null);
    }

    public DefaultCommandFactory(
            final AggregateRepository<TState, TEvent> repository,
            final List<CommandPostProcessor> postProcessors) {
        this(repository, postProcessors, RetryPolicy.noRetries());
    }

    public DefaultCommandFactory(
            final AggregateRepository<TState, TEvent> repository,
            final RetryPolicy retryPolicy
    ) {
        this(repository, null, retryPolicy);
    }

    public DefaultCommandFactory(
            final AggregateRepository<TState, TEvent> repository,
            final List<CommandPostProcessor> postProcessors,
            final RetryPolicy retryPolicy) {
        this.repository = repository;
        this.postProcessors = postProcessors == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(postProcessors);
        this.retryPolicy = retryPolicy;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <TParams> Command<TState, TParams, TEvent> fromOperation(
            final Operation<? super TState, ? super TParams, ? extends TEvent> operation) {
        // HACK: We know this case is safe based on how we use these interfaces, having the
        // wildcard types bubble through everywhere makes the code very ugly however - this is the
        // point where we convert from operation types that may use other concrete types for state
        // and parameters, into the one we know is applicable to our aggregate repository.
        Operation<TState, TParams, TEvent> typeHackedOperation =
                (Operation<TState, TParams, TEvent>) operation;
        Command<TState, TParams, TEvent> command =
                new DefaultCommand<>(repository, typeHackedOperation, retryPolicy);
        for (CommandPostProcessor postProcessor : postProcessors) {
            postProcessor.postProcessCommand(command);
        }
        return command;
    }
}
