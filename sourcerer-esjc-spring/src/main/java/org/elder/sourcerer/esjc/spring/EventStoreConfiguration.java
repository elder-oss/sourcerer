package org.elder.sourcerer.esjc.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.msemys.esjc.EventStore;
import com.github.msemys.esjc.EventStoreBuilder;
import org.elder.sourcerer.EventRepositoryFactory;
import org.elder.sourcerer.esjc.EventStoreEsjcEventRepositoryFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;

@Configuration
public class EventStoreConfiguration {
    @Bean
    @Scope(BeanDefinition.SCOPE_SINGLETON)
    public EventStore getEventStoreConnection(
            @Value("${sourcerer.eventstore.hostname:127.0.0.1}") final String hostname,
            @Value("${sourcerer.eventstore.port:1113}") final int port,
            @Value("${sourcerer.eventstore.gossipPort:2113}") final int gossipPort,
            @Value("${sourcerer.eventstore.requireMaster:false}") final boolean requireMaster,
            @Value("${sourcerer.eventstore.useClusterDiscovery:false}")
            final boolean useClusterDiscovery) {
        EventStoreBuilder builder = EventStoreBuilder
                .newBuilder()
                .userCredentials("admin", "changeit")
                .requireMaster(requireMaster)
                .failOnNoServerResponse(true);

        if (useClusterDiscovery) {
            builder
                    .clusterNodeUsingDns(dnsConfig -> dnsConfig
                            .dns(hostname)
                            .externalGossipPort(gossipPort)
                            .maxDiscoverAttempts(10)
                            .discoverAttemptInterval(Duration.ofMillis(500)));
        } else {
            builder.singleNodeAddress(hostname, port);
        }

        return builder.build();
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_SINGLETON)
    public EventRepositoryFactory getEventRepositoryFactory(
            final EventStore eventStore,
            final ObjectMapper objectMapper,
            @Value("${sourcerer.eventstore.namespace}") final String namespace) {
        return new EventStoreEsjcEventRepositoryFactory(eventStore, objectMapper, namespace.trim());
    }

    @Bean(name = "eventStoreStatus")
    @Scope(BeanDefinition.SCOPE_SINGLETON)
    public HealthIndicator getEventStoreHealthIndicator(final EventStore eventStore) {
        return new EventStoreHealthIndicator(eventStore);
    }
}
