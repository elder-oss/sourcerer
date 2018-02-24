package org.elder.soucerer.eventstore.spring;

import eventstore.Position;
import eventstore.ReadAllEventsCompleted;
import eventstore.j.EsConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.util.concurrent.ExecutionException;

public class EventStoreHealthIndicator extends AbstractHealthIndicator {
    private static final Logger logger = LoggerFactory.getLogger(EventStoreHealthIndicator.class);
    private final EsConnection connection;

    public EventStoreHealthIndicator(final EsConnection connection) {

        this.connection = connection;
    }

    @Override
    protected void doHealthCheck(final Health.Builder builder) throws Exception {
        logger.debug("Reading Event Store for health check");
        ReadAllEventsCompleted res =
                completeFuture(connection.readAllEventsBackward(
                        Position.Last$.MODULE$,
                        1,
                        false,
                        null));

        logger.debug("Event store successfully pinged");
        builder
                .status(Status.UP)
                .withDetail("position", res.position().toString());
    }

    private static <T> T completeFuture(final Future<T> future) {
        try {
            return FutureConverters.toJava(future).toCompletableFuture().get();
        } catch (InterruptedException ex) {
            throw new RuntimeException("Internal error reading event", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ex.getCause();
            } else {
                throw new RuntimeException("Internal error reading event", ex);
            }
        }
    }
}
