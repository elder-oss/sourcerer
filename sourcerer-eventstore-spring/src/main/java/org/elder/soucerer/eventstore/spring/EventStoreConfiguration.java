package org.elder.soucerer.eventstore.spring;

import akka.actor.ActorSystem;
import com.fasterxml.jackson.databind.ObjectMapper;
import eventstore.Settings;
import eventstore.cluster.ClusterSettings;
import eventstore.cluster.GossipSeedsOrDns;
import eventstore.cluster.GossipSeedsOrDns$;
import eventstore.j.EsConnection;
import eventstore.j.EsConnectionFactory;
import org.elder.sourcerer.EventRepositoryFactory;
import org.elder.sourcerer.eventstore.EventStoreEventRepositoryFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import scala.Option;
import scala.concurrent.duration.FiniteDuration;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Configuration
public class EventStoreConfiguration {
    @Bean
    @Scope(BeanDefinition.SCOPE_SINGLETON)
    public EsConnection getEventStoreConnection(
            @Value("${sourcerer.eventstore.hostname:127.0.0.1}") final String hostname,
            @Value("${sourcerer.eventstore.port:1113}") final int port,
            @Value("${sourcerer.eventstore.gossipPort:2113}") final int gossipPort,
            @Value("${sourcerer.eventstore.requireMaster:false}") final boolean requireMaster,
            @Value("${sourcerer.eventstore.useClusterDiscovery:false}")
            final boolean useClusterDiscovery) {
        ActorSystem system = ActorSystem.create();
        Settings defaultSettings = Settings.Default();
        Option<ClusterSettings> clusterSettings;

        if (useClusterDiscovery) {
            GossipSeedsOrDns clusterDns = GossipSeedsOrDns$.MODULE$.apply(hostname, gossipPort);
            clusterSettings = Option.apply(new ClusterSettings(
                    clusterDns,
                    FiniteDuration.apply(2, TimeUnit.SECONDS),
                    10,
                    FiniteDuration.apply(500, TimeUnit.MILLISECONDS),
                    FiniteDuration.apply(1000, TimeUnit.MILLISECONDS),
                    FiniteDuration.apply(1000, TimeUnit.MILLISECONDS)));
        } else {
            clusterSettings = Option.empty();
        }

        Settings settings = new Settings(
                new InetSocketAddress(hostname, port),
                defaultSettings.connectionTimeout(),
                -1,
                defaultSettings.reconnectionDelayMin(),
                defaultSettings.reconnectionDelayMax(),
                defaultSettings.defaultCredentials(),
                defaultSettings.heartbeatInterval(),
                defaultSettings.heartbeatTimeout(),
                defaultSettings.operationMaxRetries(),
                defaultSettings.operationTimeout(),
                defaultSettings.resolveLinkTos(),
                requireMaster,
                defaultSettings.readBatchSize(),
                defaultSettings.backpressure(),
                clusterSettings);
        return EsConnectionFactory.create(system, settings);
    }

    @Bean
    @Scope(BeanDefinition.SCOPE_SINGLETON)
    public EventRepositoryFactory getEventRepositoryFactory(
            final EsConnection connection,
            final ObjectMapper objectMapper,
            @Value("${sourcerer.eventstore.namespace}") final String namespace) {
        return new EventStoreEventRepositoryFactory(connection, objectMapper, namespace.trim());
    }

    @Bean(name = "eventStoreStatus")
    @Scope(BeanDefinition.SCOPE_SINGLETON)
    public HealthIndicator getEventStoreHealthIndicator(final EsConnection connection) {
        return new EventStoreHealthIndicator(connection);
    }
}
