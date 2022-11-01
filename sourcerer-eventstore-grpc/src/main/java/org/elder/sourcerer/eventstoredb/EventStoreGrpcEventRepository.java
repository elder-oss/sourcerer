package org.elder.sourcerer.eventstoredb;

import com.eventstore.dbclient.AppendToStreamOptions;
import com.eventstore.dbclient.ConnectionShutdownException;
import com.eventstore.dbclient.EventDataBuilder;
import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.ExpectedRevision;
import com.eventstore.dbclient.NotLeaderException;
import com.eventstore.dbclient.ReadResult;
import com.eventstore.dbclient.ReadStreamOptions;
import com.eventstore.dbclient.RecordedEvent;
import com.eventstore.dbclient.ResolvedEvent;
import com.eventstore.dbclient.ResourceNotFoundException;
import com.eventstore.dbclient.StreamNotFoundException;
import com.eventstore.dbclient.StreamRevision;
import com.eventstore.dbclient.SubscribeToStreamOptions;
import com.eventstore.dbclient.Subscription;
import com.eventstore.dbclient.SubscriptionListener;
import com.eventstore.dbclient.WriteResult;
import com.eventstore.dbclient.WrongExpectedVersionException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import reactor.core.publisher.FluxSink;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Sourcerer event repository implementation using EventStore (geteventstore.com) as the underlying
 * system, and the newer gRPC based Java client
 * https://github.com/EventStore/EventStoreDB-Client-Java.
 * The EventStore implementation uses Jackson to serialize and deserialize events that are
 * subclasses of the given event type base class. To instruct Jackson on how to correctly
 * deserialize events to the correct concrete sub type, please use either the Jackson annotations,
 * or JAXB annotations as per
 * <a href="http://wiki.fasterxml.com/JacksonPolymorphicDeserialization">Jackson Polymorphic
 * Deserialization</a>
 *
 * @param <T> The type of events managed by the event repository.
 */
public class EventStoreGrpcEventRepository<T> implements EventRepository<T> {
    private static final Logger logger
            = LoggerFactory.getLogger(EventStoreGrpcEventRepository.class);

    private static final int NON_EXISTING_STREAM_VERSION = -1;
    private static final int MAX_MAX_EVENTS_PER_READ = 4095;
    private static final long DEFAULT_TIMEOUT_MILLIS = 30 * (long) 1000;
    private final String streamPrefix;
    private final Class<T> eventClass;
    private final EventStoreDBClient eventStore;
    private final ObjectMapper objectMapper;
    private final EventNormalizer<T> normalizer;
    private final long timeoutMillis;

    public EventStoreGrpcEventRepository(
            final String streamPrefix,
            final EventStoreDBClient eventStore,
            final Class<T> eventClass,
            final ObjectMapper objectMapper,
            final EventNormalizer<T> normalizer) {
        this.streamPrefix = streamPrefix;
        this.eventClass = eventClass;
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
        this.normalizer = normalizer;
        this.timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
    }

    @Override
    public Class<T> getEventType() {
        return eventClass;
    }

    @Override
    public EventReadResult<T> readAll(final int version, final int maxEvents) {
        return readInternal(getCategoryStreamName(), version, maxEvents, true);
    }

    @Override
    public EventReadResult<T> read(final String streamId, final int version, final int maxEvents) {
        return readInternal(toEsStreamId(streamId), version, maxEvents, false);
    }

    @Override
    public EventRecord<T> readFirst(final String streamId) {
        return readSingleInternal(toEsStreamId(streamId), StreamRevision.START, false);
    }

    @Override
    public EventRecord<T> readLast(final String streamId) {
        return readSingleInternal(toEsStreamId(streamId), StreamRevision.END, false);
    }

    private String getCategoryStreamName() {
        return "$ce-" + streamPrefix;
    }

    private EventRecord<T> readSingleInternal(
            final String internalStreamId,
            final StreamRevision streamRevision,
            final boolean resolveLinksTo) {
        logger.debug(
                "Reading event {} from {} (in {})",
                streamRevision,
                internalStreamId,
                streamPrefix);

        ReadResult readResult = completeReadFuture(
                eventStore.readStream(
                        internalStreamId,
                        1,
                        ReadStreamOptions.get()
                                .fromRevision(streamRevision)
                                .backwards()
                                .resolveLinkTos(resolveLinksTo)),
                ExpectedVersion.any());

        if (readResult == null || readResult.getEvents() == null
                || readResult.getEvents().isEmpty()) {
            logger.debug(
                    "Reading {} (in {}) returned no event",
                    internalStreamId,
                    streamPrefix);
            return null;
        }

        ResolvedEvent event = readResult.getEvents().get(0);
        logger.debug(
                "Read event from {} (version {})",
                internalStreamId,
                event.getOriginalEvent().getStreamRevision().getValueUnsigned());
        return fromEsEvent(event);
    }

    private EventReadResult<T> readInternal(
            final String internalStreamId,
            final int version,
            final int maxEvents,
            final boolean resolveLinksTo) {
        int maxEventsPerRead = Integer.min(maxEvents, MAX_MAX_EVENTS_PER_READ);
        logger.debug(
                "Reading from {} (in {}) (version {}) - effective max {}",
                internalStreamId,
                streamPrefix,
                version,
                maxEventsPerRead);

        ReadResult readResult = completeReadFuture(
                eventStore.readStream(
                        internalStreamId,
                        maxEventsPerRead,
                        ReadStreamOptions.get()
                                .fromRevision(version)
                                .resolveLinkTos(resolveLinksTo)),
                ExpectedVersion.exactly(version));

        if (readResult == null) {
            // Not found or deleted, same thing to us!
            logger.debug(
                    "Reading {} (in {}) returned status not found",
                    internalStreamId, streamPrefix);
            return null;
        }

        logger.debug(
                "Read {} events from {} (version {})",
                readResult.getEvents().size(),
                internalStreamId,
                version);
        ImmutableList<EventRecord<T>> events = readResult
                .getEvents()
                .stream()
                .map(this::fromEsEvent)
                .collect(new ImmutableListCollector<>());

        // TODO: These may be added natively in an updated version of the Java client, see
        //   https://discuss.eventstore.com/t/support-for-detecting-a-subscription-being-live-with-grpc/4098/3
        //   for discussion
        int fromEventNumber = version;
        // NOTE: If we for some reason read with a version greater than the current last version of
        // the stream, we will incorrectly report a "last version" from the future. This will never
        // be an issue however if reading events with the normal paging pattern from the start,
        // there should be no reason to use a version "from the future" unless it is known to exist.
        int lastEventNumber = events.isEmpty()
                ? version - 1
                : events.get(events.size() - 1).getStreamVersion();
        int nextEventNumber = lastEventNumber + 1;

        return new EventReadResult<>(
                events,
                fromEventNumber,
                lastEventNumber,
                nextEventNumber,
                events.size() < maxEventsPerRead);
    }

    @Override
    public int append(
            final String streamId,
            final List<EventData<T>> events,
            final ExpectedVersion version) {
        Preconditions.checkNotNull(events);
        ElderPreconditions.checkNotEmpty(events);

        List<com.eventstore.dbclient.EventData> esEvents = events
                .stream()
                .map(this::toEsEventData)
                .collect(Collectors.toList());

        logger.debug("Writing {} events to stream {} (in {}) (expected version {})",
                esEvents.size(), streamId, streamPrefix, version);
        try {
            WriteResult result = completeWriteFuture(
                    eventStore.appendToStream(
                            toEsStreamId(streamId),
                            AppendToStreamOptions.get()
                                    .expectedRevision(toExpectedRevision(version)),
                            esEvents.iterator()),
                    version);

            int nextExpectedVersion = fromStreamRevision(result.getNextExpectedRevision());
            logger.debug("Write successful, next expected version is {}", nextExpectedVersion);
            return nextExpectedVersion;
        } catch (WrongExpectedVersionException ex) {
            logger.warn("Unexpected version when attempting append", ex);
            throw new UnexpectedVersionException(ex.getMessage(), version);
        }
    }

    @Override
    public int getCurrentVersion() {
        return getStreamVersionInternal(getCategoryStreamName());
    }

    @Override
    public int getCurrentVersion(final String streamId) {
        return getStreamVersionInternal(toEsStreamId(streamId));
    }

    private int getStreamVersionInternal(final String streamName) {
        ReadResult readResult = completeReadFuture(
                eventStore.readStream(
                        streamName,
                        1,
                        ReadStreamOptions.get()
                                .backwards()
                                .fromRevision(StreamRevision.END)
                                .notResolveLinkTos()
                ),
                ExpectedVersion.any());

        boolean hasResult = readResult != null
                && readResult.getEvents() != null
                && !readResult.getEvents().isEmpty();

        return hasResult
                ? fromStreamRevision(
                readResult.getEvents().get(0).getOriginalEvent().getStreamRevision())
                : NON_EXISTING_STREAM_VERSION;
    }

    @Override
    public Publisher<EventSubscriptionUpdate<T>> getStreamPublisher(
            final String streamId,
            final Integer fromVersion) {
        logger.info("Creating publisher for {} (in {}) (starting with version {})",
                streamId, streamPrefix, fromVersion);

        return Flux.create((FluxSink<EventSubscriptionUpdate<T>> emitter) -> {
            final Subscription subscription = completeReadFuture(
                    eventStore.subscribeToStream(
                            toEsStreamId(streamId),
                            new EmitterListener(emitter, streamPrefix + "-" + streamId),
                            SubscribeToStreamOptions.get()
                                    .fromRevision(toStreamRevision(fromVersion))
                                    .resolveLinkTos()),
                    ExpectedVersion.any());

            emitter.onCancel(() -> {
                logger.info("Closing gRPC subscription (asynchronously)");
                if (subscription != null) {
                    subscription.stop();
                }
            });
        });
    }

    @Override
    public Publisher<EventSubscriptionUpdate<T>> getPublisher(final Integer fromVersion) {
        logger.info("Creating publisher for all events in {} (starting with version {})",
                streamPrefix, fromVersion);
        return Flux.create((FluxSink<EventSubscriptionUpdate<T>> emitter) -> {
            final Subscription subscription = completeReadFuture(
                    eventStore.subscribeToStream(
                            getCategoryStreamName(),
                            new EmitterListener(emitter, streamPrefix + "-all"),
                            SubscribeToStreamOptions.get()
                                    .fromRevision(toStreamRevision(fromVersion))
                                    .resolveLinkTos()), ExpectedVersion.any());
            emitter.onCancel(() -> {
                logger.info("Closing gRPC subscription (asynchronously)");
                if (subscription != null) {
                    subscription.stop();
                }
            });
        });
    }

    private String toEsStreamId(final String streamId) {
        return streamPrefix + "-" + streamId;
    }

    private com.eventstore.dbclient.EventData toEsEventData(final EventData<T> eventData) {
        return EventDataBuilder.json(eventData.getEventType(), toEsEvent(eventData.getEvent()))
                .metadataAsBytes(toEsMetadata(eventData.getMetadata()))
                .eventId(eventData.getEventId())
                .build();
    }

    private byte[] toEsMetadata(final Map<String, String> metadata) {
        return jsonObjectToBytes(metadata);
    }

    private byte[] toEsEvent(final T event) {
        return jsonObjectToBytes(event);
    }

    private byte[] jsonObjectToBytes(final Object obj) {
        try {
            return objectMapper.writer().writeValueAsBytes(obj);
        } catch (IOException ex) {
            throw new RetriableEventWriteException("Internal error writing event", ex);
        }
    }

    private ExpectedRevision toExpectedRevision(final ExpectedVersion version) {
        if (version == null) {
            return ExpectedRevision.ANY;
        } else {
            switch (version.getType()) {
                case ANY:
                    return ExpectedRevision.ANY;
                case EXACTLY:
                    return ExpectedRevision.expectedRevision(version.getExpectedVersion());
                case NOT_CREATED:
                    return ExpectedRevision.NO_STREAM;
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
        long streamVersion;
        long aggregateVersion;
        if (event.getLink() != null) {
            aggregateVersion = event.getEvent().getStreamRevision().getValueUnsigned();
            streamVersion = event.getLink().getStreamRevision().getValueUnsigned();
        } else {
            aggregateVersion = event.getEvent().getStreamRevision().getValueUnsigned();
            streamVersion = event.getEvent().getStreamRevision().getValueUnsigned();
        }

        return new EventRecord<>(
                fromEsStreamId(event.getEvent().getStreamId()),
                convertTo32Bit(streamVersion),
                convertTo32Bit(aggregateVersion),
                event.getEvent().getEventType(),
                event.getEvent().getEventId(),
                event.getEvent().getCreated(),
                fromEsMetadata(event.getEvent().getUserMetadata()),
                fromEsData(event.getEvent().getEventData()));
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
            return ImmutableMap.copyOf((Map<String, String>) objectMapper
                    .readerFor(new TypeReference<Map<String, String>>() {
                    })
                    .readValue(metadata));
        } catch (IOException ex) {
            throw new RetriableEventReadException("Internal error reading events", ex);
        }
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
            if (ex.getCause() instanceof StreamNotFoundException) {
                // This is expected in some cases, flag with null rather than error
                return null;
            } else if (ex.getCause() instanceof WrongExpectedVersionException) {
                throw new UnexpectedVersionException(ex.getCause(), expectedVersion);
            } else if (ex.getCause() instanceof ResourceNotFoundException
            ) {
                throw new PermanentEventReadException(ex.getCause());
            } else if (ex.getCause() instanceof ConnectionShutdownException
                    || ex.getCause() instanceof NotLeaderException
            ) {
                throw new RetriableEventReadException(ex.getCause());
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
            if (ex.getCause() instanceof WrongExpectedVersionException) {
                throw new UnexpectedVersionException(ex.getCause(), expectedVersion);
            } else if (ex.getCause() instanceof ResourceNotFoundException
                    || ex.getCause() instanceof StreamNotFoundException) {
                throw new PermanentEventWriteException(ex.getCause());
            } else if (ex.getCause() instanceof ConnectionShutdownException
                    || ex.getCause() instanceof NotLeaderException) {
                throw new RetriableEventWriteException(ex.getCause());
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

    private static int convertTo32Bit(final long longPosition) {
        if (longPosition > (long) Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "Server returned a 64 bit position greater than what could be converted to " +
                            "a 64 bit number, this suggest that you have a stream (individual or " +
                            "projection) with more than 2 billion events. If you really need " +
                            "this, please fork Sourcerer and change ints to be longs.");
        }

        return (int) longPosition;
    }

    private int fromStreamRevision(final StreamRevision revision) {
        return convertTo32Bit(revision.getValueUnsigned());
    }

    private StreamRevision toStreamRevision(final Integer version) {
        return version == null ? StreamRevision.START : new StreamRevision(version);
    }

    private class EmitterListener extends SubscriptionListener {
        private final FluxSink<EventSubscriptionUpdate<T>> emitter;
        private final String name;

        public EmitterListener(
                final FluxSink<EventSubscriptionUpdate<T>> emitter,
                final String name) {
            this.emitter = emitter;
            this.name = name;
        }

        @Override
        public void onEvent(final Subscription subscription, final ResolvedEvent event) {
            logger.debug("Incoming message in {}: {}", name, event);
            try {
                emitter.next(EventSubscriptionUpdate.ofEvent(fromEsEvent(event)));
            } catch (final Exception ex) {
                final RecordedEvent recordedEvent = event.getEvent();
                final String eventType = recordedEvent == null ? null
                        : recordedEvent.getEventType();
                final String streamId = recordedEvent == null ? null
                        : recordedEvent.getStreamId();
                final String position = recordedEvent == null ? null
                        : recordedEvent.getPosition().toString();
                final String msg = String.format(
                        "Subscription %s failed onEvent, emitting error. " +
                                "EventType: %s, StreamId: %s, Position: %s",
                        name,
                        eventType,
                        streamId,
                        position
                );
                logger.error(msg, ex);
                emitter.error(ex);
            }
            // TODO: Support "caught up" tracking once new Java client is live
        }

        @Override
        public void onError(final Subscription subscription, final Throwable exception) {
            if (exception != null) {
                logger.error(
                        "Subscription " + name + " failed with reason " +
                                exception.getMessage() + "",
                        exception);
            } else {
                logger.error(
                        "Subscription {} failed with reason {} and no exception",
                        name,
                        "unknown");
            }

            emitter.error(new EventStoreSubscriptionStoppedException(exception));
        }

        @Override
        public void onCancelled(final Subscription subscription) {
            logger.error("Subscription " + name + " was cancelled");
        }
    }
}
