package org.elder.sourcerer;

import com.google.common.collect.ImmutableList;

import java.util.List;

public class DefaultCommandFactory<TAggregate, TEvent>
        implements CommandFactory<TAggregate, TEvent> {
    private final AggregateRepository<TAggregate, TEvent> repository;
    private final List<CommandPostProcessor> postProcessors;

    public DefaultCommandFactory(final AggregateRepository<TAggregate, TEvent> repository) {
        this(repository, null);
    }

    public DefaultCommandFactory(
            final AggregateRepository<TAggregate, TEvent> repository,
            final List<CommandPostProcessor> postProcessors) {
        this.repository = repository;
        this.postProcessors = postProcessors == null
                ? ImmutableList.of()
                : ImmutableList.copyOf(postProcessors);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <TParams> Command<TAggregate, TParams, TEvent> fromOperation(
            final Operation<? super TAggregate, ? super TParams, ? extends TEvent> operation) {
        // HACK: We know this case is safe based on how we use these interfaces, having the
        // wildcard types bubble through everywhere makes the code very ugly however - this is the
        // point where we convert from operation types that may use other concrete types for state
        // and parameters, into the one we know is applicable to our aggregate repository.
        Operation<TAggregate, TParams, TEvent> typeHackedOperation =
                (Operation<TAggregate, TParams, TEvent>) operation;
        Command<TAggregate, TParams, TEvent> command =
                new DefaultCommand<TAggregate, TParams, TEvent>(repository, typeHackedOperation);
        for (CommandPostProcessor postProcessor : postProcessors) {
            postProcessor.postProcessCommand(command);
        }
        return command;
    }
}
