package org.elder.sourcerer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default implementation of AggregateRepository, expressed in terms of EventRepository, and
 * AggregateProjection.
 */
public class DefaultAggregateRepository<TState, TEvent>
        implements AggregateRepository<TState, TEvent> {
    private static final Logger logger = LoggerFactory.getLogger(DefaultAggregateRepository.class);
    private final EventRepository<TEvent> eventRepository;
    private final AggregateProjection<TState, TEvent> projection;
    private final Function<? super TEvent, String> typeResolver;

    public DefaultAggregateRepository(
            final EventRepository<TEvent> eventRepository,
            final AggregateProjection<TState, TEvent> projection) {
        this(eventRepository, projection, DefaultAggregateRepository::defaultResolveType);
    }

    public DefaultAggregateRepository(
            final EventRepository<TEvent> eventRepository,
            final AggregateProjection<TState, TEvent> projection,
            final Function<? super TEvent, String> typeResolver) {
        this.eventRepository = eventRepository;
        this.projection = projection;
        this.typeResolver = typeResolver;
    }

    @Override
    public AggregateRecord<TState> read(final String aggregateId) {
        TState aggregate = null;
        int currentStreamPosition = 0;
        while (true) { // Exit through return
            logger.debug("Reading events for {} from {}", aggregateId, currentStreamPosition);
            EventReadResult<TEvent> readResult = eventRepository.read(
                    aggregateId,
                    currentStreamPosition);

            if (readResult == null || readResult.getEvents().isEmpty()) {
                // Not found, return empty wrapper as per contract
                return new AggregateRecord<>(null, -1);
            }

            Iterable<TEvent> events = readResult
                    .getEvents()
                    .stream()
                    .map(EventRecord::getEvent)
                    .collect(Collectors.toList());
            aggregate = projection.apply(aggregateId, aggregate, events);

            if (readResult.isEndOfStream()) {
                return new AggregateRecord<>(aggregate, readResult.getLastVersion());
            }

            currentStreamPosition = readResult.getNextVersion();
            logger.warn(
                    "Reading large stream from {}, now reading from {} - consider snapshotting?",
                    aggregateId,
                    currentStreamPosition);
        }
    }

    @Override
    public int update(
            final String aggregateId,
            final List<? extends TEvent> events,
            final ExpectedVersion expectedVersion,
            final Map<String, String> metadata) {
        List<EventData<TEvent>> eventDatas = events
                .stream()
                .map(e -> new EventData<TEvent>(
                        getEventType(e),
                        UUID.randomUUID(),
                        metadata,
                        e))
                .collect(Collectors.toList());
        return eventRepository.append(aggregateId, eventDatas, expectedVersion);
    }

    private String getEventType(final TEvent event) {
        return typeResolver.apply(event);
    }

    private static String defaultResolveType(final Object event) {
        return event.getClass().getTypeName();
    }
}
