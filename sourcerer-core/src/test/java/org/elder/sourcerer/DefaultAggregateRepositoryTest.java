package org.elder.sourcerer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
        repository = new DefaultAggregateRepository<>(eventRepository, aggregateProjection);
    }

    @Test
    public void readReturnsRecordWithNullOnNullStream() throws Exception {
        returnsRecordWithNullOn(null);
    }

    @Test
    public void readReturnsNullOnEmptyStream() throws Exception {
        returnsRecordWithNullOn(ImmutableList.of());
    }

    private void returnsRecordWithNullOn(final List<EventRecord<TestEvent>> readEventsResult) {
        when(eventRepository.read(any())).thenReturn(null);

        AggregateRecord<TestState> aggregate = repository.read(AGGREGATE_ID_1);

        verify(eventRepository).read(AGGREGATE_ID_1, 0);
        verifyNoMoreInteractions(eventRepository);
        Assert.assertNotNull(aggregate);
        Assert.assertNull(aggregate.getAggregate());
        Assert.assertEquals(-1, aggregate.getVersion());
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

        when(eventRepository.read(any(), anyInt())).thenReturn(repositoryResult);
        when(aggregateProjection.apply(any(), any(), (Iterable) any())).thenReturn(expectedState);

        AggregateRecord<TestState> result = repository.read(AGGREGATE_ID_1);

        verify(eventRepository).read(AGGREGATE_ID_1, 0);
        ArgumentCaptor<Iterable<TestEvent>> passedEvents
                = ArgumentCaptor.forClass((Class) Iterable.class);
        verify(aggregateProjection).apply(eq(AGGREGATE_ID_1), eq(null), passedEvents.capture());
        Assert.assertThat(
                passedEvents.getValue(),
                org.hamcrest.Matchers.contains(expectedEvents.toArray()));

        verifyNoMoreInteractions(eventRepository, aggregateProjection);
        Assert.assertEquals(expectedState, result.getAggregate());
    }

    @Test
    public void versionPassedOnAndReturned() {
        ExpectedVersion expectedVersion = exactly(42);
        int newVersion = 45;
        when(eventRepository.append(any(), anyList(), any())).thenReturn(newVersion);

        int actualNewVersion
                = repository.update(AGGREGATE_ID_1, new TestEvent("test"), expectedVersion);

        verify(eventRepository).append(eq(AGGREGATE_ID_1), anyList(), eq(expectedVersion));
        Assert.assertEquals(newVersion, actualNewVersion);
    }

    @Test
    public void updatePassesOnEventsToEventRepository() {
        List<TestEvent> events = Arrays.asList(new TestEvent("test1"), new TestEvent("test2"));

        when(eventRepository.append(any(), anyList(), any())).thenReturn(42);

        repository.update(AGGREGATE_ID_1, events, ExpectedVersion.any());

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

        when(eventRepository.read(any(), anyInt())).thenReturn(
                repositoryResult1,
                repositoryResult2);
        when(aggregateProjection.apply(any(), any(), (Iterable) any())).thenReturn(expectedState);

        repository.read(AGGREGATE_ID_1);
        verify(aggregateProjection, times(2)).apply(any(), any(), (Iterable) any());
        verify(eventRepository).read(AGGREGATE_ID_1, 0);
        verify(eventRepository).read(AGGREGATE_ID_1, 2);
        verifyNoMoreInteractions(aggregateProjection, eventRepository);
    }
}