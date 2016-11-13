package org.elder.sourcerer.eventstore;

import akka.pattern.AskTimeoutException;
import akka.util.ByteString;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import eventstore.Content;
import eventstore.Event;
import eventstore.EventNumber;
import eventstore.ReadStreamEventsCompleted;
import eventstore.ResolvedEvent;
import eventstore.WriteResult;
import eventstore.WrongExpectedVersionException;
import eventstore.j.ContentType;
import eventstore.j.EsConnection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import org.elder.sourcerer.EventData;
import org.elder.sourcerer.EventNormalizer;
import org.elder.sourcerer.EventReadResult;
import org.elder.sourcerer.EventRecord;
import org.elder.sourcerer.EventRepository;
import org.elder.sourcerer.EventSubscriptionUpdate;
import org.elder.sourcerer.ExpectedVersion;
import org.elder.sourcerer.exceptions.RetriableEventReadException;
import org.elder.sourcerer.exceptions.RetriableEventWriteException;
import org.elder.sourcerer.exceptions.UnexpectedVersionException;
import org.elder.sourcerer.utils.ElderPreconditions;
import org.elder.sourcerer.utils.ImmutableListCollector;
import org.joda.time.DateTime;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import scala.Option;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sourcerer event repository implementation using EventStore (geteventstore.com) as the underlying
 * system. The EventStore implementation uses Jackson to serialize and deserialize events that are
 * subclasses of the given event type base class. To instruct Jackson on how to correctly
 * deserialize events to the correct concrete sub type, please use either the Jaconson annotations,
 * or JAXB annotations as per <a href="http://wiki.fasterxml.com/JacksonPolymorphicDeserialization">Jackson
 * Polymorphic Deserialization</a>
 *
 * @param <T> The type of events managed by the event repository.
 */
public class EventStoreEventRepository<T> implements EventRepository<T> {
    private static final int MAX_MAX_EVENTS_PER_READ = 4095;
    private static final int DEFAULT_MAX_SERIALIZED_EVENT_SIZE = 256 * 1024;
    private static final Logger logger = LoggerFactory.getLogger(EventStoreEventRepository.class);
    private final String streamPrefix;
    private final Class<T> eventClass;
    private final EventNormalizer<T> normalizer;
    private final EsConnection connection;
    private final ObjectMapper objectMapper;
    private final PooledByteBufAllocator bufferAllocator;
    private final int maxSerializedEventSize;

    public EventStoreEventRepository(
            final String streamPrefix,
            final EsConnection connection,
            final Class<T> eventClass,
            final ObjectMapper objectMapper,
            final EventNormalizer<T> normalizer) {
        this.streamPrefix = streamPrefix;
        this.eventClass = eventClass;
        this.normalizer = normalizer;
        this.connection = connection;
        this.objectMapper = objectMapper;
        this.maxSerializedEventSize = DEFAULT_MAX_SERIALIZED_EVENT_SIZE;
        this.bufferAllocator = PooledByteBufAllocator.DEFAULT;
    }

    @Override
    public EventReadResult<T> read(final String streamId, final int version, final int maxEvents) {
        try {
            int maxEventsPerRead = Integer.min(maxEvents, MAX_MAX_EVENTS_PER_READ);
            logger.debug(
                    "Reading from {} (in {}) (version {}) - effective max {}",
                    streamId,
                    streamPrefix,
                    version,
                    maxEventsPerRead);
            ReadStreamEventsCompleted res = completeReadFuture(connection.readStreamEventsForward(
                    toEsStreamId(streamId),
                    new EventNumber.Exact(version),
                    maxEventsPerRead,
                    false,
                    null));

            logger.debug(
                    "Read {} events from {} (version {})",
                    res.eventsJava().size(),
                    streamId,
                    version);
            ImmutableList<EventRecord<T>> events = res
                    .eventsJava()
                    .stream()
                    .map(this::fromEsEvent)
                    .collect(new ImmutableListCollector<>());
            return new EventReadResult<>(
                    events,
                    version,
                    res.lastEventNumber().value(),
                    ((EventNumber.Exact) res.nextEventNumber()).value(),
                    res.endOfStream());
        } catch (eventstore.StreamNotFoundException ex) {
            return null;
        }
    }

    @Override
    public int append(
            final String streamId,
            final List<EventData<T>> events,
            final ExpectedVersion version) {
        Preconditions.checkNotNull(events);
        ElderPreconditions.checkNotEmpty(events);

        List<eventstore.EventData> esEvents = events
                .stream()
                .map(this::toEsEventData)
                .collect(Collectors.toList());

        logger.debug("Writing {} events to stream {} (in {}) (expected version {})",
                     esEvents.size(), streamId, streamPrefix, version);
        try {
            WriteResult result = completeWriteFuture(connection.writeEvents(
                    toEsStreamId(streamId),
                    toEsVersion(version),
                    esEvents,
                    null));
            if (result == null) {
                logger.warn("Null result - when does this happen!?");
                throw new RetriableEventWriteException("Unknown write status, null result!");
            }

            int nextExpectedVersion = result.nextExpectedVersion().value();
            logger.debug("Write successful, next expected version is {}", nextExpectedVersion);
            return nextExpectedVersion;
        } catch (WrongExpectedVersionException ex) {
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
        Publisher<Event> esPublisher = connection.streamPublisher(
                toEsStreamId(streamId),
                fromVersion == null ? null : new EventNumber.Exact(fromVersion),
                false,
                null,
                true);
        return Flux.from(esPublisher)
                .map(this::fromEsEvent)
                // This implementation does not support caught up status, each append is an event
                .map(EventSubscriptionUpdate::ofEvent);
    }

    @Override
    public Publisher<EventSubscriptionUpdate<T>> getPublisher(final Integer fromVersion) {
        logger.info("Creating publisher for all events in {} (starting with version {})",
                    streamPrefix, fromVersion);
        Publisher<Event> esPublisher = connection.streamPublisher(
                "$ce-" + streamPrefix,
                fromVersion == null ? null : new EventNumber.Exact(fromVersion),
                true,
                null,
                true);
        return Flux.from(esPublisher)
                .map(this::fromEsEvent)
                // This implementation does not support caught up status, each append is an event
                .map(EventSubscriptionUpdate::ofEvent);
    }

    private String toEsStreamId(final String streamId) {
        return streamPrefix + "-" + streamId;
    }

    private eventstore.EventData toEsEventData(final EventData<T> eventData) {
        return new eventstore.EventData(
                eventData.getEventType(),
                eventData.getEventId(),
                toEsEvent(eventData.getEvent()),
                toEsMetadata(eventData.getMetadata()));
    }

    private Content toEsMetadata(final Map<String, String> metadata) {
        return jsonObjectToContent(metadata);
    }

    private Content toEsEvent(final T event) {
        return jsonObjectToContent(event);
    }

    private Content jsonObjectToContent(final Object obj) {
        return withBuffer(buffer -> {
            try {
                OutputStream outputStream = new ByteBufOutputStream(buffer);
                objectMapper.writer().writeValue(outputStream, obj);
                ByteString byteString = ByteString.fromByteBuffer(buffer.nioBuffer());
                return new Content(byteString, ContentType.json());
            } catch (IOException ex) {
                throw new RetriableEventWriteException("Internal error writing event", ex);
            }
        });
    }

    private eventstore.ExpectedVersion toEsVersion(final ExpectedVersion version) {
        if (version == null) {
            return new eventstore.ExpectedVersion.Any$();
        } else {
            switch (version.getType()) {
                case ANY:
                    return new eventstore.ExpectedVersion.Any$();
                case EXACTLY:
                    return new eventstore.ExpectedVersion.Exact(version.getExpectedVersion());
                case NOT_CREATED:
                    return new eventstore.ExpectedVersion.NoStream$();
                default:
                    throw new IllegalArgumentException(
                            "Unrecognized expected version type: " + version);
            }
        }
    }

    private <U> U withBuffer(final Function<ByteBuf, U> function) {
        ByteBuf buffer = null;
        try {
            buffer = bufferAllocator.directBuffer(maxSerializedEventSize, maxSerializedEventSize);
            return function.apply(buffer);
        } finally {
            if (buffer != null) {
                buffer.release();
            }
        }
    }

    private String fromEsStreamId(final String streamId) {
        // TODO: Ensure that we have a dash, handle mulitple ones sanely
        return streamId.substring(streamId.indexOf('-') + 1);
    }

    private EventRecord<T> fromEsEvent(final Event event) {
        int streamVersion;
        int aggregateVersion;
        if (event instanceof ResolvedEvent) {
            ResolvedEvent resolvedEvent = (ResolvedEvent) event;
            aggregateVersion = resolvedEvent.linkedEvent().number().value();
            streamVersion = resolvedEvent.linkEvent().number().value();
        } else {
            aggregateVersion = event.number().value();
            streamVersion = event.number().value();
        }

        return new EventRecord<>(
                fromEsStreamId(event.streamId().streamId()),
                streamVersion,
                aggregateVersion,
                event.data().eventType(),
                event.data().eventId(),
                fromEsTimestamp(event.created()),
                fromEsMetadata(event.data().metadata()),
                fromEsDataContent(event.data().data()));
    }

    private T fromEsDataContent(final Content data) {
        try {
            T rawEvent = objectMapper
                    .readerFor(eventClass)
                    .readValue(data.value().iterator().asInputStream());
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
    private ImmutableMap<String, String> fromEsMetadata(final Content metadata) {
        if (metadata == null
                || metadata.contentType() != ContentType.json()
                || metadata.value().size() == 0) {
            return ImmutableMap.of();
        }

        try {
            return ImmutableMap.copyOf((Map) objectMapper
                    .readerFor(new TypeReference<Map<String, String>>() {
                    })
                    .readValue(metadata.value().iterator().asInputStream()));
        } catch (IOException ex) {
            throw new RetriableEventReadException("Internal error reading events", ex);
        }
    }

    private static Instant fromEsTimestamp(final Option<DateTime> created) {
        if (!created.isDefined()) {
            throw new IllegalStateException(
                    "No time stamp returned from EventStore where expected");
        }

        return Instant.ofEpochMilli(created.get().toInstant().getMillis());
    }

    private static <T> T completeReadFuture(final Future<T> future) {
        try {
            return FutureConverters.toJava(future).toCompletableFuture().get();
        } catch (InterruptedException ex) {
            throw new RetriableEventReadException("Internal error reading event", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ex.getCause();
            } else if (ex.getCause() instanceof AskTimeoutException) {
                throw new RetriableEventReadException("Timeout reading events", ex.getCause());
            } else {
                throw new RetriableEventReadException(
                        "Internal error reading events",
                        ex.getCause());
            }
        }
    }

    private static <T> T completeWriteFuture(final Future<T> future) {
        try {
            return FutureConverters.toJava(future).toCompletableFuture().get();
        } catch (InterruptedException ex) {
            throw new RetriableEventWriteException("Internal error writing event", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ex.getCause();
            } else if (ex.getCause() instanceof AskTimeoutException) {
                throw new RetriableEventWriteException("Timeout writing events", ex.getCause());
            } else {
                throw new RetriableEventWriteException(
                        "Internal error writing events",
                        ex.getCause());
            }
        }
    }
}
