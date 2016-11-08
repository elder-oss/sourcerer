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
    public <TParams> Command<TAggregate, TParams, TEvent> fromOperation(
            final Operation<? super TAggregate, ? super TParams, ? extends TEvent> operation) {
        Command<TAggregate, TParams, TEvent> command = new DefaultCommand<>(repository, operation);
        for (CommandPostProcessor postProcessor : postProcessors) {
            postProcessor.postProcessCommand(command);
        }
        return command;
    }
}
