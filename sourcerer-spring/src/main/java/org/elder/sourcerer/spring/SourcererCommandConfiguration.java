package org.elder.sourcerer.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.elder.sourcerer.AggregateProjection;
import org.elder.sourcerer.AggregateRepository;
import org.elder.sourcerer.CommandFactory;
import org.elder.sourcerer.CommandPostProcessor;
import org.elder.sourcerer.DefaultAggregateRepository;
import org.elder.sourcerer.DefaultCommandFactory;
import org.elder.sourcerer.MetadataDecorator;
import org.elder.sourcerer.EventRepository;
import org.elder.sourcerer.MetadataDecoratorCommandPostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class for Spring Java configurations defining all required components surrounding a given
 * event and aggregate type, i.e. aggregate repository, event repository, command factory and
 * subscription factory.
 * <p>
 * Components are lazy loaded so as to not use any resources unless actually used in the system
 * <p>
 * To use this class, create a subclass with the given concrete types and any other customizations,
 * placed in the component scan path of your Spring application. You must also separately configure
 * a EventRepositoryFactory.
 */
public class SourcererCommandConfiguration<TEvent, TState>
        extends SourcererEventConfiguration<TEvent> {
    private final Class<TState> aggregateType;
    private final AggregateProjection<TState, TEvent> projection;

    @Autowired(required = false)
    private List<MetadataDecorator> metadataDecorators;

    @Autowired
    private ObjectMapper objectMapper;

    protected SourcererCommandConfiguration(
            final Class<TEvent> eventType,
            final Class<TState> aggregateType,
            final AggregateProjection<TState, TEvent> projection) {
        super(eventType);
        this.aggregateType = aggregateType;
        this.projection = projection;
    }

    @Bean
    @Lazy
    @Scope(BeanDefinition.SCOPE_SINGLETON)
    public AggregateRepository<TState, TEvent> getAggregateRepository(
            final EventRepository<TEvent> eventRepository) {
        return new DefaultAggregateRepository<>(eventRepository, projection, this::resolveType);
    }

    @Bean
    @Lazy
    @Scope(BeanDefinition.SCOPE_SINGLETON)
    public CommandFactory<TState, TEvent> getCommandFactory(
            final AggregateRepository<TState, TEvent> aggregateRepository) {
        List<CommandPostProcessor> commandPostProcessors = new ArrayList<>();
        if (metadataDecorators != null) {
            for (MetadataDecorator metadataDecorator : metadataDecorators) {
                commandPostProcessors.add(
                        new MetadataDecoratorCommandPostProcessor(metadataDecorator));
            }
        }

        return new DefaultCommandFactory<>(aggregateRepository, commandPostProcessors);
    }

    public String resolveType(final TEvent event) {
        Map jsonMap = objectMapper.convertValue(event, Map.class);
        if (!jsonMap.containsKey("type")) {
            throw new IllegalArgumentException(
                    "Event does not appear to use polymorphic Jackson serialization"
                    + ", override resolveType() to provide a different implementation");
        }

        return (String) jsonMap.get("type");
    }
}
