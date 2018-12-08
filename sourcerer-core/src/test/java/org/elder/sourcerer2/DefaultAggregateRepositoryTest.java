package org.elder.sourcerer2;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class DefaultAggregateRepositoryTest {
    private static final StreamId AGGREGATE_ID_1 = StreamId.ofString("very-much-id-1");
    private EventRepository<TestEvent> eventRepository;
    private AggregateProjection<TestState, TestEvent> aggregateProjection;
    private DefaultAggregateRepository<TestState, TestEvent> repository;

    @Before
    @SuppressWarnings("unchecked")
    public void setUpRepository() {
        eventRepository = mock(EventRepository.class);
        aggregateProjection = mock(AggregateProjection.class);
        when(aggregateProjection.empty()).thenReturn(new TestState("empty"));
        repository = new DefaultAggregateRepository<>(eventRepository, aggregateProjection);
    }

    @Test
    public void readReturnsRecordWithEmptyOnNullStream() throws Exception {
        returnsAggregateWithEmptyStateOn(null);
    }

    @Test
    public void readReturnsEmptyOnEmptyStream() throws Exception {
        returnsAggregateWithEmptyStateOn(
                new StreamReadResult(
                        ImmutableList.of(),
                        StreamVersion.ofString("11"),
                        false));
    }

    private void returnsAggregateWithEmptyStateOn(
            final StreamReadResult readEventsResult) {
        when(eventRepository.read(any(), any(), anyInt())).thenReturn(readEventsResult);
        ImmutableAggregate<TestState, TestEvent> aggregate = repository.load(AGGREGATE_ID_1);

        verify(eventRepository).read(eq(AGGREGATE_ID_1), isNull(StreamVersion.class), anyInt());
        verifyNoMoreInteractions(eventRepository);
        Assert.assertNotNull(aggregate);
        Assert.assertEquals(aggregate.state().getValue(), "empty");
        Assert.assertEquals(-1, aggregate.sourceVersion());
    }

    @Test
    public void returnsResultOfProjection() {
        TestEvent testEvent1 = new TestEvent("test1");
        TestEvent testEvent2 = new TestEvent("test2");
        ImmutableList<EventRecord<TestEvent>> events = ImmutableList.of(
                simpleEventRecord(
                        AGGREGATE_ID_1,
                        "test1",
                        0,
                        100,
                        testEvent1),
                simpleEventRecord(
                        AGGREGATE_ID_1,
                        "test1",
                        1,
                        101,
                        testEvent2));
        StreamReadResult repositoryResult = new StreamReadResult<>(
                events,
                StreamVersion.ofInt(1),
                true);
        List<TestEvent> expectedEvents = ImmutableList.of(testEvent1, testEvent2);
        TestState expectedState = new TestState("state1");

        when(eventRepository.read(any(), any(), anyInt())).thenReturn(repositoryResult);
        when(aggregateProjection.apply(any(), any(), (Iterable) any())).thenReturn(expectedState);

        ImmutableAggregate<TestState, TestEvent> result = repository.load(AGGREGATE_ID_1);

        verify(eventRepository).read(eq(AGGREGATE_ID_1), isNull(StreamVersion.class), anyInt());
        ArgumentCaptor<Iterable<TestEvent>> passedEvents
                = ArgumentCaptor.forClass((Class) Iterable.class);
        verify(aggregateProjection).empty();
        verify(aggregateProjection).apply(
                eq(AGGREGATE_ID_1),
                eq(new TestState("empty")),
                passedEvents.capture());
        Assert.assertThat(
                passedEvents.getValue(),
                org.hamcrest.Matchers.contains(expectedEvents.toArray()));

        verifyNoMoreInteractions(eventRepository, aggregateProjection);
        Assert.assertEquals(expectedState, result.state());
    }

    @Test
    public void versionPassedOnAndReturned() {
        StreamVersion beforeVersion = StreamVersion.ofInt(42);
        ExpectedVersion expectedVersion = ExpectedVersion.exactly(beforeVersion);
        StreamVersion newVersion = StreamVersion.ofInt(45);
        when(eventRepository.append(any(), anyList(), any())).thenReturn(newVersion);

        StreamVersion actualNewVersion
                = repository.append(AGGREGATE_ID_1, new TestEvent("test"), expectedVersion);

        verify(eventRepository).append(eq(AGGREGATE_ID_1), anyList(), eq(expectedVersion));
        Assert.assertEquals(newVersion, actualNewVersion);
    }

    @Test
    public void updatePassesOnEventsToEventRepository() {
        List<TestEvent> events = Arrays.asList(new TestEvent("test1"), new TestEvent("test2"));

        StreamVersion beforeVersion = StreamVersion.ofInt(42);
        when(eventRepository.append(any(), anyList(), any())).thenReturn(beforeVersion);

        repository.append(AGGREGATE_ID_1, events, ExpectedVersion.any());

        ArgumentCaptor<List<EventData<TestEvent>>> passedEvents
                = ArgumentCaptor.forClass((Class) List.class);
        verify(eventRepository).append(eq(AGGREGATE_ID_1), passedEvents.capture(), any());
        Assert.assertThat(
                passedEvents
                        .getValue()
                        .stream()
                        .map(EventData::getEvent)
                        .collect(Collectors.toList()),
                org.hamcrest.Matchers.contains(events.toArray()));
    }

    @Test
    public void partialReadGetsPages() {
        TestEvent testEvent1 = new TestEvent("test1");
        TestEvent testEvent2 = new TestEvent("test2");
        TestEvent testEvent3 = new TestEvent("test3");
        ImmutableList<EventRecord<TestEvent>> batch1Events = ImmutableList.of(
                simpleEventRecord(
                        AGGREGATE_ID_1,
                        "test1",
                        3,
                        100,
                        testEvent1),
                simpleEventRecord(
                        AGGREGATE_ID_1,
                        "test1",
                        4,
                        101,
                        testEvent2));
        ImmutableList<EventRecord<TestEvent>> batch2Events = ImmutableList.of(
                simpleEventRecord(
                        AGGREGATE_ID_1,
                        "test1",
                        5,
                        102,
                        testEvent3));

        StreamReadResult<TestEvent> repositoryResult1 =
                new StreamReadResult<>(batch1Events, StreamVersion.ofInt(4), false);
        StreamReadResult<TestEvent> repositoryResult2 =
                new StreamReadResult<>(batch2Events, StreamVersion.ofInt(5), true);
        TestState expectedState = new TestState("state1");

        when(eventRepository.read(any(), any(), anyInt())).thenReturn(
                repositoryResult1,
                repositoryResult2);
        when(aggregateProjection.apply(any(), any(), (Iterable) any())).thenReturn(expectedState);

        repository.load(AGGREGATE_ID_1);
        verify(aggregateProjection).empty();
        verify(aggregateProjection, times(2)).apply(any(), any(), (Iterable) any());
        verify(eventRepository).read(eq(AGGREGATE_ID_1), isNull(StreamVersion.class), anyInt());
        verify(eventRepository).read(eq(AGGREGATE_ID_1), eq(StreamVersion.ofInt(4)), anyInt());
        verifyNoMoreInteractions(aggregateProjection, eventRepository);
    }

    private <T> EventRecord<T> simpleEventRecord(
            final StreamId aggregateId,
            final String eventType,
            final int streamVersion,
            final int repositoryVersion,
            final T event
    ) {
        return new EventRecord<>(
                EventId.newUniqueId(),
                aggregateId,
                StreamVersion.ofInt(streamVersion),
                RepositoryVersion.ofInt(repositoryVersion),
                eventType,
                Instant.now(),
                ImmutableMap.of(),
                event);
    }
}
