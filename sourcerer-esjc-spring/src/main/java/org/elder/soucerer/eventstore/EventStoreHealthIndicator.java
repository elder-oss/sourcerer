package org.elder.soucerer.eventstore;

import com.github.msemys.esjc.AllEventsSlice;
import com.github.msemys.esjc.EventStore;
import com.github.msemys.esjc.Position;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public class EventStoreHealthIndicator implements HealthIndicator {
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 250;
    private static final Logger logger = LoggerFactory.getLogger(EventStoreHealthIndicator.class);
    private final EventStore eventStore;
    private final int readTimeoutMillis;
    private final Function<Supplier<Health>, Health> adapter;

    /**
     * Creates a new EventStore health indicator with a default timeout.
     *
     * @param eventStore The EventStore client instance to use for health checks.
     */
    public EventStoreHealthIndicator(@NotNull final EventStore eventStore) {
        this(eventStore, DEFAULT_READ_TIMEOUT_MILLIS);
    }

    /**
     * Creates a new EventStore health indicator.
     *
     * @param eventStore        The EventStore client instance to use for health checks.
     * @param readTimeoutMillis Timeout for health checks passing, if a simple call to EventStore
     */
    public EventStoreHealthIndicator(
            @NotNull final EventStore eventStore,
            final int readTimeoutMillis) {
        this(eventStore, readTimeoutMillis, null);
    }

    /**
     * Creates a new EventStore health indicator.
     *
     * @param eventStore        The EventStore client instance to use for health checks.
     * @param readTimeoutMillis Timeout for health checks passing, if a simple call to EventStore
     *                          takes longer than this number of milliseconds (or fails with an
     *                          error), the health check will fail.
     * @param adapter           Optional wrapper for health checks, to inject additional monitoring,
     *                          logging, and/or checks.
     */
    public EventStoreHealthIndicator(
            @NotNull final EventStore eventStore,
            final int readTimeoutMillis,
            final Function<Supplier<Health>, Health> adapter
    ) {
        this.eventStore = eventStore;
        this.readTimeoutMillis = readTimeoutMillis;
        if (adapter != null) {
            this.adapter = adapter;
        } else {
            this.adapter = Supplier::get;
        }
    }

    @Override
    public Health health() {
        return adapter.apply(this::runHealthProbe);
    }

    private Health runHealthProbe() {
        logger.debug("Reading Event Store for health check");
        try {
            final AllEventsSlice currentEventInfo = readLastEventBlocking();

            logger.debug("Event store successfully pinged");
            return Health
                    .up()
                    .withDetail("position", currentEventInfo.nextPosition.toString())
                    .build();
        } catch (final Exception ex) {
            logger.warn("Error in EventStore health check, marking as down", ex);
            return Health.down(ex).build();
        }
    }

    private AllEventsSlice readLastEventBlocking() throws Exception {
        final CompletableFuture<AllEventsSlice> futureResult =
                eventStore.readAllEventsBackward(Position.END, 1, false);
        return futureResult.get(readTimeoutMillis, TimeUnit.MILLISECONDS);
    }
}
