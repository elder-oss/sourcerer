package org.elder.sourcerer2;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Default implementation of AggregateRepository, expressed in terms of EventRepository, and
 * AggregateProjection.
 */
public class DefaultAggregateRepository<TState, TEvent>
        implements AggregateRepository<TState, TEvent> {
    private static final int DEFAULT_MAX_EVENTS_PER_READ = Integer.MAX_VALUE;
    private static final int LARGE_EVENT_STREAM_WARNING_CUTOFF = 1000;
    private static final Logger logger = LoggerFactory.getLogger(DefaultAggregateRepository.class);
    private final EventRepository<TEvent> eventRepository;
    private final AggregateProjection<TState, TEvent> projection;
    private final Function<? super TEvent, String> typeResolver;
    private final int maxEventsPerRead;

    /**
     * Creates a new aggregate repository, reading events from the provided event repository and
     * constructing aggregates from it using the provided projection.
     *
     * @param eventRepository The event repository to use to load events from, and persist events
     *                        to, the event storage mechanism used.
     * @param projection      The aggregate projection used to recreate aggregate state from a
     *                        sequence of events.
     */
    public DefaultAggregateRepository(
            final EventRepository<TEvent> eventRepository,
            final AggregateProjection<TState, TEvent> projection
    ) {
        this(eventRepository, projection, DEFAULT_MAX_EVENTS_PER_READ);
    }

    /**
     * Creates a new aggregate repository, reading events from the provided event repository and
     * constructing aggregates from it using the provided projection.
     *
     * @param eventRepository  The event repository to use to load events from, and persist events
     *                         to, the event storage mechanism used.
     * @param projection       The aggregate projection used to recreate aggregate state from a
     *                         sequence of events.
     * @param maxEventsPerRead The maximum number of events to read in one go from the underlying
     *                         event store. Note that, regardless of this value, all events for a
     *                         given stream will eventually be read and applied to the provided
     *                         projection, this flag can be used to control the maximum number of
     *                         events that are kept in memory at any given point in time.
     */
    public DefaultAggregateRepository(
            final EventRepository<TEvent> eventRepository,
            final AggregateProjection<TState, TEvent> projection,
            final int maxEventsPerRead
    ) {
        this(
                eventRepository,
                projection,
                DefaultAggregateRepository::defaultResolveType,
                maxEventsPerRead);
    }

    /**
     * Creates a new aggregate repository, reading events from the provided event repository and
     * constructing aggregates from it using the provided projection.
     *
     * @param eventRepository The event repository to use to load events from, and persist events
     *                        to, the event storage mechanism used.
     * @param projection      The aggregate projection used to recreate aggregate state from a
     *                        sequence of events.
     * @param typeResolver    A function used to determine the string event type name for a given
     *                        runtime instance of an event.
     */
    public DefaultAggregateRepository(
            final EventRepository<TEvent> eventRepository,
            final AggregateProjection<TState, TEvent> projection,
            final Function<? super TEvent, String> typeResolver
    ) {
        this(
                eventRepository,
                projection,
                typeResolver,
                DEFAULT_MAX_EVENTS_PER_READ);
    }

    /**
     * Creates a new aggregate repository, reading events from the provided event repository and
     * constructing aggregates from it using the provided projection.
     *
     * @param eventRepository  The event repository to use to load events from, and persist events
     *                         to, the event storage mechanism used.
     * @param projection       The aggregate projection used to recreate aggregate state from a
     *                         sequence of events.
     * @param maxEventsPerRead The maximum number of events to read in one go from the underlying
     *                         event store. Note that, regardless of this value, all events for a
     *                         given stream will eventually be read and applied to the provided
     *                         projection, this flag can be used to control the maximum number of
     *                         events that are kept in memory at any given point in time.
     * @param typeResolver     A function used to determine the string event type name for a given
     *                         runtime instance of an event.
     */
    public DefaultAggregateRepository(
            final EventRepository<TEvent> eventRepository,
            final AggregateProjection<TState, TEvent> projection,
            final Function<? super TEvent, String> typeResolver,
            final int maxEventsPerRead
    ) {
        this.eventRepository = eventRepository;
        this.projection = projection;
        this.typeResolver = typeResolver;
        this.maxEventsPerRead = maxEventsPerRead;
    }

    @Override
    public ImmutableAggregate<TState, TEvent> load(@NotNull final StreamId aggregateId) {
        TState state = projection.empty();
        StreamVersion currentStreamPosition = null;
        int eventsRead = 0;

        try {
            while (true) { // Exit through return
                logger.debug("Reading events for {} from {}", aggregateId, currentStreamPosition);
                StreamReadResult<TEvent> readResult = eventRepository.read(
                        aggregateId,
                        currentStreamPosition,
                        maxEventsPerRead);

                if (readResult == null || readResult.getEvents().isEmpty()) {
                    // Not found, return empty wrapper as per contract
                    return DefaultImmutableAggregate.createNew(
                            projection,
                            aggregateId);
                }

                List<TEvent> events = readResult
                        .getEvents()
                        .stream()
                        .map(EventRecord::getEvent)
                        .collect(Collectors.toList());
                state = projection.apply(aggregateId, state, events);
                eventsRead += events.size();
                currentStreamPosition = readResult.getVersion();

                if (readResult.isEndOfStream()) {
                    return DefaultImmutableAggregate.fromExisting(
                            projection,
                            aggregateId,
                            currentStreamPosition,
                            state);
                }
            }
        } finally {
            if (eventsRead > LARGE_EVENT_STREAM_WARNING_CUTOFF) {
                logger.warn(
                        "Read large stream {} consisting of {} events - consider snapshotting?",
                        aggregateId,
                        eventsRead);
            }
        }
    }

    @Override
    @NotNull
    public StreamVersion append(
            @NotNull final StreamId aggregateId,
            @NotNull final Iterable<? extends TEvent> events,
            @NotNull final ExpectedVersion expectedVersion,
            final Map<String, String> metadata
    ) {
        List<EventData<TEvent>> eventDatas = StreamSupport
                .stream(events.spliterator(), false)
                .map(e -> new EventData<TEvent>(
                        EventId.newUniqueId(),
                        getEventType(e),
                        metadata != null ? ImmutableMap.copyOf(metadata) : ImmutableMap.of(),
                        e))
                .collect(Collectors.toList());
        return eventRepository.append(aggregateId, eventDatas, expectedVersion);
    }

    @Override
    @NotNull
    public ImmutableAggregate<TState, TEvent> save(
            @NotNull final Aggregate<TState, TEvent> aggregate,
            final boolean atomic,
            final Map<String, String> metadata
    ) {
        Preconditions.checkNotNull(aggregate);
        ExpectedVersion expectedVersion;
        if (atomic) {
            if (aggregate.sourceVersion() == null) {
                expectedVersion = ExpectedVersion.notCreated();
            } else {
                expectedVersion = ExpectedVersion.exactly(aggregate.sourceVersion());
            }
        } else {
            expectedVersion = ExpectedVersion.any();
        }
        StreamVersion newVersion =
                append(aggregate.id(), aggregate.events(), expectedVersion, metadata);
        return DefaultImmutableAggregate.fromExisting(
                projection,
                aggregate.id(),
                newVersion,
                aggregate.state());
    }

    private String getEventType(final TEvent event) {
        return typeResolver.apply(event);
    }

    private static String defaultResolveType(final Object event) {
        return event.getClass().getTypeName();
    }
}
