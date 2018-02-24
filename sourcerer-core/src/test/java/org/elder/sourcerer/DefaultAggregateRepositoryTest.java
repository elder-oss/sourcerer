package org.elder.sourcerer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.elder.sourcerer.ExpectedVersion.exactly;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class DefaultAggregateRepositoryTest {
    private static final String AGGREGATE_ID_1 = "very-much-id-1";
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
                new EventReadResult(
                        ImmutableList.of(),
                        0, 10, 11, false));
    }

    private void returnsAggregateWithEmptyStateOn(
            final EventReadResult readEventsResult) {
        when(eventRepository.read(any(), anyInt(), anyInt())).thenReturn(readEventsResult);
        ImmutableAggregate<TestState, TestEvent> aggregate = repository.load(AGGREGATE_ID_1);

        verify(eventRepository).read(eq(AGGREGATE_ID_1), eq(0), anyInt());
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
                new EventRecord<>(
                        AGGREGATE_ID_1, 3, 1, "test1", UUID.randomUUID(),
                        Instant.now(), ImmutableMap.of(), testEvent1),
                new EventRecord<>(
                        AGGREGATE_ID_1, 4, 1, "test1", UUID.randomUUID(),
                        Instant.now(), ImmutableMap.of(), testEvent2));
        EventReadResult repositoryResult = new EventReadResult<>(events, 0, 1, 2, true);
        List<TestEvent> expectedEvents = ImmutableList.of(testEvent1, testEvent2);
        TestState expectedState = new TestState("state1");

        when(eventRepository.read(any(), anyInt(), anyInt())).thenReturn(repositoryResult);
        when(aggregateProjection.apply(any(), any(), (Iterable) any())).thenReturn(expectedState);

        ImmutableAggregate<TestState, TestEvent> result = repository.load(AGGREGATE_ID_1);

        verify(eventRepository).read(eq(AGGREGATE_ID_1), eq(0), anyInt());
        ArgumentCaptor<Iterable<TestEvent>> passedEvents
                = ArgumentCaptor.forClass((Class) Iterable.class);
        verify(aggregateProjection).empty();
        verify(aggregateProjection).apply(
                eq(AGGREGATE_ID_1),
                eq(new TestState("empty")),
                passedEvents.capture());
        Assertions
                .assertThat(passedEvents.getValue())
                .contains(expectedEvents.toArray(new TestEvent[0]));

        verifyNoMoreInteractions(eventRepository, aggregateProjection);
        Assert.assertEquals(expectedState, result.state());
    }

    @Test
    public void versionPassedOnAndReturned() {
        ExpectedVersion expectedVersion = exactly(42);
        int newVersion = 45;
        when(eventRepository.append(any(), anyList(), any())).thenReturn(newVersion);

        int actualNewVersion
                = repository.append(AGGREGATE_ID_1, new TestEvent("test"), expectedVersion);

        verify(eventRepository).append(eq(AGGREGATE_ID_1), anyList(), eq(expectedVersion));
        Assert.assertEquals(newVersion, actualNewVersion);
    }

    @Test
    public void updatePassesOnEventsToEventRepository() {
        List<TestEvent> events = Arrays.asList(new TestEvent("test1"), new TestEvent("test2"));

        when(eventRepository.append(any(), anyList(), any())).thenReturn(42);

        repository.append(AGGREGATE_ID_1, events, ExpectedVersion.any());

        ArgumentCaptor<List<EventData<TestEvent>>> passedEvents
                = ArgumentCaptor.forClass((Class) List.class);
        verify(eventRepository).append(eq(AGGREGATE_ID_1), passedEvents.capture(), any());
        Assertions
                .assertThat(
                passedEvents
                        .getValue()
                        .stream()
                        .map(EventData::getEvent)
                        .collect(Collectors.toList()))
                .contains(events.toArray(new TestEvent[0]));
    }

    @Test
    public void partialReadGetsPages() {
        TestEvent testEvent1 = new TestEvent("test1");
        TestEvent testEvent2 = new TestEvent("test2");
        TestEvent testEvent3 = new TestEvent("test3");
        ImmutableList<EventRecord<TestEvent>> batch1Events = ImmutableList.of(
                new EventRecord<>(
                        AGGREGATE_ID_1, 3, 1, "test1", UUID.randomUUID(),
                        Instant.now(), ImmutableMap.of(), testEvent1),
                new EventRecord<>(
                        AGGREGATE_ID_1, 4, 1, "test1", UUID.randomUUID(),
                        Instant.now(), ImmutableMap.of(), testEvent2));
        ImmutableList<EventRecord<TestEvent>> batch2Events = ImmutableList.of(
                new EventRecord<>(
                        AGGREGATE_ID_1, 3, 1, "test1", UUID.randomUUID(),
                        Instant.now(), ImmutableMap.of(), testEvent3));

        EventReadResult<TestEvent> repositoryResult1 =
                new EventReadResult<>(batch1Events, 0, 1, 2, false);
        EventReadResult<TestEvent> repositoryResult2 =
                new EventReadResult<>(batch2Events, 2, 2, 3, true);
        TestState expectedState = new TestState("state1");

        when(eventRepository.read(any(), anyInt(), anyInt())).thenReturn(
                repositoryResult1,
                repositoryResult2);
        when(aggregateProjection.apply(any(), any(), (Iterable) any())).thenReturn(expectedState);

        repository.load(AGGREGATE_ID_1);
        verify(aggregateProjection).empty();
        verify(aggregateProjection, times(2)).apply(any(), any(), (Iterable) any());
        verify(eventRepository).read(eq(AGGREGATE_ID_1), eq(0), anyInt());
        verify(eventRepository).read(eq(AGGREGATE_ID_1), eq(2), anyInt());
        verifyNoMoreInteractions(aggregateProjection, eventRepository);
    }
}
