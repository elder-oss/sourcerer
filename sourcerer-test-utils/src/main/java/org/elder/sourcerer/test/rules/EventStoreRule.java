package org.elder.sourcerer.test.rules;

import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.shazam.tocker.AliveStrategies;
import com.shazam.tocker.DockerInstance;
import com.shazam.tocker.PortMap;
import com.shazam.tocker.RunningDockerInstance;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * JUnit rule for starting and stopping an EventStore instance using docker.
 */
public class EventStoreRule extends ExternalResource {
    private static final int SERVICE_PORT = 1113;
    private static final int PROBE_PORT = 2113;
    private static final Logger logger = LoggerFactory.getLogger(EventStoreRule.class);
    private final DockerInstance instance = DockerInstance
            .fromImage("eventstore/eventstore:release-4.0.1")
            .withContainerName("eventstore-test-container")
            .mappingPorts(
                    PortMap.ephemeral(SERVICE_PORT),
                    PortMap.ephemeral(PROBE_PORT))
            .build();
    private RunningDockerInstance store;
    private List<Runnable> cleanup = new ArrayList<>();

    @Override
    protected void before() throws Throwable {
        logger.info("Starting EventStore");
        store = instance.run(AliveStrategies.retrying(livenessProbe(), 100, 100));
    }

    @Override
    protected void after() {
        for (Runnable job : cleanup) {
            job.run();
        }
    }

    public EventStoreRule start() {
        return this;
    }

    private Function<RunningDockerInstance, Boolean> livenessProbe() {
        return inst -> {
            int port = inst.mappedPorts().forContainerPort(PROBE_PORT);
            try {
                Unirest
                        .get(String.format("http://%s:%d/streams/newstream2", hostname(), port))
                        .asJson();
                return true;
            } catch (UnirestException ex) {
                logger.info("Liveness probe failed", ex.getMessage());
                return false;
            }
        };
    }

    public String hostname() {
        return instance.host();
    }

    public int port() {
        return store.mappedPorts().forContainerPort(SERVICE_PORT);
    }
}
