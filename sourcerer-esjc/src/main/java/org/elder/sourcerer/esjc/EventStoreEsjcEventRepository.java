package org.elder.sourcerer.esjc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.msemys.esjc.CannotEstablishConnectionException;
import com.github.msemys.esjc.CatchUpSubscription;
import com.github.msemys.esjc.CatchUpSubscriptionListener;
import com.github.msemys.esjc.CatchUpSubscriptionSettings;
import com.github.msemys.esjc.ConnectionClosedException;
import com.github.msemys.esjc.EventStore;
import com.github.msemys.esjc.EventStoreException;
import com.github.msemys.esjc.ResolvedEvent;
import com.github.msemys.esjc.SliceReadStatus;
import com.github.msemys.esjc.StreamEventsSlice;
import com.github.msemys.esjc.SubscriptionDropReason;
import com.github.msemys.esjc.WriteResult;
import com.github.msemys.esjc.node.cluster.ClusterException;
import com.github.msemys.esjc.operation.AccessDeniedException;
import com.github.msemys.esjc.operation.CommandNotExpectedException;
import com.github.msemys.esjc.operation.InvalidTransactionException;
import com.github.msemys.esjc.operation.NoResultException;
import com.github.msemys.esjc.operation.NotAuthenticatedException;
import com.github.msemys.esjc.operation.ServerErrorException;
import com.github.msemys.esjc.operation.StreamDeletedException;
import com.github.msemys.esjc.operation.WrongExpectedVersionException;
import com.github.msemys.esjc.operation.manager.OperationTimedOutException;
import com.github.msemys.esjc.operation.manager.RetriesLimitReachedException;
import com.github.msemys.esjc.subscription.MaximumSubscribersReachedException;
import com.github.msemys.esjc.subscription.PersistentSubscriptionDeletedException;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.elder.sourcerer.EventData;
import org.elder.sourcerer.EventNormalizer;
import org.elder.sourcerer.EventReadResult;
import org.elder.sourcerer.EventRecord;
import org.elder.sourcerer.EventRepository;
import org.elder.sourcerer.EventSubscriptionUpdate;
import org.elder.sourcerer.ExpectedVersion;
import org.elder.sourcerer.exceptions.PermanentEventReadException;
import org.elder.sourcerer.exceptions.PermanentEventWriteException;
import org.elder.sourcerer.exceptions.RetriableEventReadException;
import org.elder.sourcerer.exceptions.RetriableEventWriteException;
import org.elder.sourcerer.exceptions.UnexpectedVersionException;
import org.elder.sourcerer.utils.ElderPreconditions;
import org.elder.sourcerer.utils.ImmutableListCollector;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Sourcerer event repository implementation using EventStore (geteventstore.com) as the underlying
 * system. The EventStore implementation uses Jackson to serialize and deserialize events that are
 * subclasses of the given event type base class. To instruct Jackson on how to correctly
 * deserialize events to the correct concrete sub type, please use either the Jaconson annotations,
 * or JAXB annotations as per
 * <a href="http://wiki.fasterxml.com/JacksonPolymorphicDeserialization">Jackson Polymorphic
 * Deserialization</a>
 *
 * @param <T> The type of events managed by the event repository.
 */
public class EventStoreEsjcEventRepository<T> implements EventRepository<T> {
    private static final Logger logger
            = LoggerFactory.getLogger(EventStoreEsjcEventRepository.class);

    private static final int MAX_MAX_EVENTS_PER_READ = 4095;
    private static final long DEFAULT_TIMEOUT_MILLIS = 30 * (long) 1000;
    private final String streamPrefix;
    private final Class<T> eventClass;
    private final EventStore eventStore;
    private final ObjectMapper objectMapper;
    private final EventNormalizer<T> normalizer;
    private final long timeoutMillis;
    private final CatchUpSubscriptionSettings defaultSubscriptionSettings;

    public EventStoreEsjcEventRepository(
            final String streamPrefix,
            final EventStore eventStore,
            final Class<T> eventClass,
            final ObjectMapper objectMapper,
            final EventNormalizer<T> normalizer) {
        this.streamPrefix = streamPrefix;
        this.eventClass = eventClass;
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
        this.normalizer = normalizer;
        this.timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        // TODO: Customize these settings
        defaultSubscriptionSettings = CatchUpSubscriptionSettings.newBuilder()
                .resolveLinkTos(true)
                .build();
    }

    @Override
    public EventReadResult<T> read(final String streamId, final int version, final int maxEvents) {
        int maxEventsPerRead = Integer.min(maxEvents, MAX_MAX_EVENTS_PER_READ);
        logger.debug(
                "Reading from {} (in {}) (version {}) - effective max {}",
                streamId,
                streamPrefix,
                version,
                maxEventsPerRead);

        StreamEventsSlice eventsSlice = completeReadFuture(
                eventStore.readStreamEventsForward(
                        toEsStreamId(streamId),
                        version,
                        maxEventsPerRead,
                        false),
                ExpectedVersion.exactly(version));

        if (eventsSlice.status != SliceReadStatus.Success) {
            // Not found or deleted, same thing to us!
            logger.debug(
                    "Reading {} (in {}) returned status {}",
                    streamId, streamPrefix, eventsSlice.status);
            return null;
        }

        logger.debug(
                "Read {} events from {} (version {})",
                eventsSlice.events.size(),
                streamId,
                version);
        ImmutableList<EventRecord<T>> events = eventsSlice
                .events
                .stream()
                .map(this::fromEsEvent)
                .collect(new ImmutableListCollector<>());
        return new EventReadResult<>(
                events,
                eventsSlice.fromEventNumber,
                eventsSlice.lastEventNumber,
                eventsSlice.nextEventNumber,
                eventsSlice.isEndOfStream);
    }

    @Override
    public int append(
            final String streamId,
            final List<EventData<T>> events,
            final ExpectedVersion version) {
        Preconditions.checkNotNull(events);
        ElderPreconditions.checkNotEmpty(events);

        List<com.github.msemys.esjc.EventData> esEvents = events
                .stream()
                .map(this::toEsEventData)
                .collect(Collectors.toList());

        logger.debug("Writing {} events to stream {} (in {}) (expected version {})",
                     esEvents.size(), streamId, streamPrefix, version);
        try {
            WriteResult result = completeWriteFuture(
                    eventStore.appendToStream(
                            toEsStreamId(streamId),
                            toEsVersion(version),
                            esEvents),
                    version);

            int nextExpectedVersion = result.nextExpectedVersion;
            logger.debug("Write successful, next expected version is {}", nextExpectedVersion);
            return nextExpectedVersion;
        } catch (WrongExpectedVersionException ex) {
            logger.warn("Unexpected version when attempting append", ex);
            throw new UnexpectedVersionException(
                    ex.getMessage(),
                    null,
                    version);
        }
    }

    @Override
    public Publisher<EventSubscriptionUpdate<T>> getStreamPublisher(
            final String streamId,
            final Integer fromVersion) {
        logger.info("Creating publisher for {} (in {}) (starting with version {})",
                    streamId, streamPrefix, fromVersion);

        return Flux.create(emitter -> {
            final CatchUpSubscription subscription = eventStore.subscribeToStreamFrom(
                    toEsStreamId(streamId),
                    fromVersion,
                    defaultSubscriptionSettings,
                    new EmitterListener(emitter, streamPrefix + "-" + streamId));
            emitter.setCancellation(() -> {
                logger.info("Closing ESJC subscription (asynchronously)");
                subscription.stop();
            });
        });
    }

    @Override
    public Publisher<EventSubscriptionUpdate<T>> getPublisher(final Integer fromVersion) {
        logger.info("Creating publisher for all events in {} (starting with version {})",
                    streamPrefix, fromVersion);
        return Flux.create(emitter -> {
            final CatchUpSubscription subscription = eventStore.subscribeToStreamFrom(
                    "$ce-" + streamPrefix,
                    fromVersion,
                    defaultSubscriptionSettings,
                    new EmitterListener(emitter, streamPrefix + "-all"));
            emitter.setCancellation(() -> {
                logger.info("Closing ESJC subscription (asynchronously)");
                subscription.stop();
            });
        });
    }

    private String toEsStreamId(final String streamId) {
        return streamPrefix + "-" + streamId;
    }

    private com.github.msemys.esjc.EventData toEsEventData(final EventData<T> eventData) {
        return com.github.msemys.esjc.EventData.newBuilder()
                .eventId(eventData.getEventId())
                .type(eventData.getEventType())
                .jsonData(toEsEvent(eventData.getEvent()))
                .jsonMetadata(toEsMetadata(eventData.getMetadata()))
                .build();
    }

    private String toEsMetadata(final Map<String, String> metadata) {
        return jsonObjectToString(metadata);
    }

    private String toEsEvent(final T event) {
        return jsonObjectToString(event);
    }

    private String jsonObjectToString(final Object obj) {
        try {
            return objectMapper.writer().writeValueAsString(obj);
        } catch (IOException ex) {
            throw new RetriableEventWriteException("Internal error writing event", ex);
        }
    }

    private com.github.msemys.esjc.ExpectedVersion toEsVersion(final ExpectedVersion version) {
        if (version == null) {
            return com.github.msemys.esjc.ExpectedVersion.any();
        } else {
            switch (version.getType()) {
                case ANY:
                    return com.github.msemys.esjc.ExpectedVersion.any();
                case EXACTLY:
                    return com.github.msemys.esjc.ExpectedVersion.of(version.getExpectedVersion());
                case NOT_CREATED:
                    return com.github.msemys.esjc.ExpectedVersion.noStream();
                default:
                    throw new IllegalArgumentException(
                            "Unrecognized expected version type: " + version);
            }
        }
    }

    private String fromEsStreamId(final String streamId) {
        // TODO: Ensure that we have a dash, handle mulitple ones sanely
        return streamId.substring(streamId.indexOf('-') + 1);
    }

    private EventRecord<T> fromEsEvent(final ResolvedEvent event) {
        int streamVersion;
        int aggregateVersion;
        if (event.isResolved()) {
            aggregateVersion = event.event.eventNumber;
            streamVersion = event.link.eventNumber;
        } else {
            aggregateVersion = event.event.eventNumber;
            streamVersion = event.event.eventNumber;
        }

        return new EventRecord<>(
                fromEsStreamId(event.event.eventStreamId),
                streamVersion,
                aggregateVersion,
                event.event.eventType,
                event.event.eventId,
                fromEsTimestamp(event.event.created),
                fromEsMetadata(event.event.metadata),
                fromEsData(event.event.data));
    }

    private T fromEsData(final byte[] data) {
        try {
            T rawEvent = objectMapper
                    .readerFor(eventClass)
                    .readValue(data);
            return normalizeEvent(rawEvent);
        } catch (IOException ex) {
            throw new RetriableEventReadException("Internal error reading events", ex);
        }
    }

    private T normalizeEvent(final T rawEvent) {
        if (normalizer != null) {
            return normalizer.normalizeEvent(rawEvent);
        } else {
            return rawEvent;
        }
    }

    @SuppressWarnings("unchecked")
    private ImmutableMap<String, String> fromEsMetadata(final byte[] metadata) {
        if (metadata == null || metadata.length == 0) {
            return ImmutableMap.of();
        }

        try {
            return ImmutableMap.copyOf((Map) objectMapper
                    .readerFor(new TypeReference<Map<String, String>>() {
                    })
                    .readValue(metadata));
        } catch (IOException ex) {
            throw new RetriableEventReadException("Internal error reading events", ex);
        }
    }

    private static Instant fromEsTimestamp(final Optional<Instant> created) {
        if (!created.isPresent()) {
            throw new IllegalStateException(
                    "No time stamp returned from EventStore where expected");
        }

        return created.get();
    }

    private <U> U completeReadFuture(
            final CompletableFuture<U> future,
            final ExpectedVersion expectedVersion) {
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RetriableEventReadException("Internal error reading event", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof EventStoreException) {
                if (ex.getCause() instanceof WrongExpectedVersionException) {
                    throw new UnexpectedVersionException(
                            ex.getCause(),
                            null,
                            expectedVersion);
                } else if (ex.getCause() instanceof AccessDeniedException
                        || ex.getCause() instanceof CommandNotExpectedException
                        || ex.getCause() instanceof InvalidTransactionException
                        || ex.getCause() instanceof NoResultException
                        || ex.getCause() instanceof NotAuthenticatedException
                        || ex.getCause() instanceof PersistentSubscriptionDeletedException
                        || ex.getCause() instanceof StreamDeletedException) {
                    throw new PermanentEventReadException(ex.getCause());
                } else if (ex.getCause() instanceof CannotEstablishConnectionException
                        || ex.getCause() instanceof ClusterException
                        || ex.getCause() instanceof ConnectionClosedException
                        || ex.getCause() instanceof OperationTimedOutException
                        || ex.getCause() instanceof MaximumSubscribersReachedException
                        || ex.getCause() instanceof RetriesLimitReachedException
                        || ex.getCause() instanceof ServerErrorException) {
                    throw new RetriableEventReadException(ex.getCause());
                } else {
                    logger.warn("Unrecognized event store exception reading events", ex.getCause());
                    throw new RetriableEventReadException(ex.getCause());
                }
            } else if (ex.getCause() instanceof RuntimeException) {
                logger.warn("Unrecognized runtime exception reading events", ex.getCause());
                throw new RetriableEventReadException(ex.getCause());
            } else {
                logger.warn("Unrecognized exception reading events", ex.getCause());
                throw new RetriableEventReadException(
                        "Internal error reading events",
                        ex.getCause());
            }
        } catch (TimeoutException ex) {
            throw new RetriableEventReadException("Timeout reading events", ex.getCause());
        }
    }

    private <U> U completeWriteFuture(
            final CompletableFuture<U> future,
            final ExpectedVersion expectedVersion) {
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RetriableEventWriteException("Internal error writing event", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof EventStoreException) {
                if (ex.getCause() instanceof WrongExpectedVersionException) {
                    throw new UnexpectedVersionException(
                            ex.getCause(),
                            null,
                            expectedVersion);
                } else if (ex.getCause() instanceof AccessDeniedException
                        || ex.getCause() instanceof CommandNotExpectedException
                        || ex.getCause() instanceof InvalidTransactionException
                        || ex.getCause() instanceof NoResultException
                        || ex.getCause() instanceof NotAuthenticatedException
                        || ex.getCause() instanceof PersistentSubscriptionDeletedException
                        || ex.getCause() instanceof StreamDeletedException) {
                    throw new PermanentEventWriteException(ex.getCause());
                } else if (ex.getCause() instanceof CannotEstablishConnectionException
                        || ex.getCause() instanceof ClusterException
                        || ex.getCause() instanceof ConnectionClosedException
                        || ex.getCause() instanceof OperationTimedOutException
                        || ex.getCause() instanceof MaximumSubscribersReachedException
                        || ex.getCause() instanceof RetriesLimitReachedException
                        || ex.getCause() instanceof ServerErrorException) {
                    throw new RetriableEventWriteException(ex.getCause());
                } else {
                    logger.warn("Unrecognized event store exception writing events", ex.getCause());
                    throw new RetriableEventWriteException(ex.getCause());
                }
            } else if (ex.getCause() instanceof RuntimeException) {
                logger.warn("Unrecognized runtime exception writing events", ex.getCause());
                throw new RetriableEventWriteException(ex.getCause());
            } else {
                logger.warn("Unrecognized exception writing events", ex.getCause());
                throw new RetriableEventWriteException(
                        "Internal error writing events",
                        ex.getCause());
            }
        } catch (TimeoutException ex) {
            throw new RetriableEventWriteException("Timeout writing events", ex.getCause());
        }
    }

    private class EmitterListener implements CatchUpSubscriptionListener {
        private final FluxEmitter<EventSubscriptionUpdate<T>> emitter;
        private final String name;

        public EmitterListener(
                final FluxEmitter<EventSubscriptionUpdate<T>> emitter,
                final String name) {
            this.emitter = emitter;
            this.name = name;
        }

        @Override
        public void onEvent(final CatchUpSubscription subscription, final ResolvedEvent event) {
            logger.debug("Incoming message in {}: {}", name, event);
            emitter.next(EventSubscriptionUpdate.ofEvent(fromEsEvent(event)));
        }

        @Override
        public void onClose(
                final CatchUpSubscription subscription,
                final SubscriptionDropReason reason,
                final Exception exception) {
            if (exception != null) {
                logger.error(
                        "Subscription " + name + " failed with reason " + reason + "",
                        exception);
            } else {
                logger.error(
                        "Subscription {} failed with reason {} and no exception",
                        name,
                        reason);
            }

            emitter.fail(new EventStoreSubscriptionStoppedException(reason, exception));
        }

        @Override
        public void onLiveProcessingStarted(final CatchUpSubscription subscription) {
            logger.info("Live processing started for {}!", name);
            emitter.next(EventSubscriptionUpdate.caughtUp());
        }
    }
}
