package org.elder.sourcerer.spring;

import org.elder.sourcerer.DefaultEventSubscriptionFactory;
import org.elder.sourcerer.EventRepository;
import org.elder.sourcerer.EventRepositoryFactory;
import org.elder.sourcerer.EventSubscriptionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

/**
 * Base class for Spring Java configurations defining all required components surrounding a given
 * event, i.e. event repository, and event subscription factory.
 * <p>
 * Components are lazy loaded so as to not use any resources unless actually used in the system
 * <p>
 * To use this class, create a subclass with the given concrete types and any other customizations,
 * placed in the component scan path of your Spring application. You must also separately configure
 * a EventRepositoryFactory.
 */
public class SourcererEventConfiguration<T> {
    private final Class<T> eventType;

    @Autowired
    private EventRepositoryFactory repositoryFactory;

    protected SourcererEventConfiguration(final Class<T> eventType) {
        this.eventType = eventType;
    }

    @Bean
    @Lazy
    @Scope(BeanDefinition.SCOPE_SINGLETON)
    public EventRepository<T> getEventRepository() {
        return repositoryFactory.getEventRepository(eventType);
    }

    @Bean
    @Lazy
    @Scope(BeanDefinition.SCOPE_SINGLETON)
    EventSubscriptionFactory<T> getEventSubscriptionFactory(
            final EventRepository<T> eventRepository) {
        return new DefaultEventSubscriptionFactory<>(eventRepository);
    }
}

