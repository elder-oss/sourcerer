package org.elder.soucerer.eventstore;

import com.github.msemys.esjc.AllEventsSlice;
import com.github.msemys.esjc.EventStore;
import com.github.msemys.esjc.Position;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class EventStoreHealthIndicator extends AbstractHealthIndicator {
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 1000;
    private static final Logger logger = LoggerFactory.getLogger(EventStoreHealthIndicator.class);
    private static final String ELDER_NAMESPACE = "elder";
    private static final Histogram latencyHistogram = Histogram.build()
            .namespace(ELDER_NAMESPACE)
            .name("eventstore_healthcheck_latency_seconds")
            .help("Delay in probing EventStore as part of health check")
            .register();

    private static final Counter probesCounter = Counter.build()
            .namespace(ELDER_NAMESPACE)
            .name("eventstore_healthchecks_total")
            .labelNames("status")
            .help("Total number of EventStore health checks with status (success or failure)")
            .register();

    private final EventStore eventStore;
    private final int readTimeoutMillis;

    public EventStoreHealthIndicator(final EventStore eventStore) {
        this(eventStore, DEFAULT_READ_TIMEOUT_MILLIS);
    }

    public EventStoreHealthIndicator(final EventStore eventStore, final int readTimeoutMillis) {
        this.eventStore = eventStore;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Override
    protected void doHealthCheck(final Health.Builder builder) throws Exception {
        logger.debug("Reading Event Store for health check");
        final Histogram.Timer latencyTimer = latencyHistogram.startTimer();
        boolean successful = false;
        try {
            AllEventsSlice res = completeReadFuture(
                    eventStore.readAllEventsBackward(Position.END, 1, false));

            logger.debug("Event store successfully pinged");
            builder
                    .status(Status.UP)
                    .withDetail("position", res.nextPosition.toString());
            probesCounter.labels("up").inc();
            successful = true;
        } finally {
            if (!successful) {
                probesCounter.labels("down").inc();
            }
            latencyTimer.observeDuration();
        }
    }

    private <U> U completeReadFuture(final CompletableFuture<U> future) throws Exception {
        try {
            return future.get(readTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            logger.warn("Error reading from eventstore, check will fail", ex);
            throw ex;
        }
    }
}
