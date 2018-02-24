package org.elder.sourcerer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.elder.sourcerer.exceptions.ConflictingExpectedVersionsException;
import org.elder.sourcerer.exceptions.InvalidCommandException;
import org.elder.sourcerer.exceptions.UnexpectedVersionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ExpectedVersion.class)
public class DefaultCommandTest {
    private static final String AGGREGATE_ID = "42";
    private AggregateRepository repository;

    @Before
    public void setUp() {
        this.repository = mock(AggregateRepository.class);
    }

    @Test(expected = InvalidCommandException.class)
    @SuppressWarnings("unchecked")
    public void conflictingVersionsGiveInvalidCommand() {
        PowerMockito.mockStatic(ExpectedVersion.class);
        PowerMockito.when(ExpectedVersion.merge(any(), any()))
                .thenThrow(new ConflictingExpectedVersionsException(
                        "error", null, null));

        Operation operation = new OperationHandlerOperation(
                (x, y) -> null,
                true,
                false,
                ExpectedVersion.exactly(42));
        DefaultCommand command = new DefaultCommand(repository, operation);
        command.setAggregateId(AGGREGATE_ID);
        command.run();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void idempotentCreateMeansNoOpIfExisting() {
        Operation operation = new OperationHandlerOperation(
                (x, y) -> ImmutableList.of(new TestEvent("xxx")),
                false,
                false,
                ExpectedVersion.notCreated());
        when(repository.load(any()))
                .thenReturn(DefaultImmutableAggregate.fromExisting(
                        mock(AggregateProjection.class),
                        AGGREGATE_ID,
                        42,
                        new TestState("test")));
        DefaultCommand command = new DefaultCommand(repository, operation);
        command.setAggregateId(AGGREGATE_ID);
        command.setIdempotentCreate(true);
        CommandResult commandResult = command.run();

        Assert.assertTrue(commandResult.getEvents().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void idempotentCreateSuccessfulIfNotPresent() {
        int newVersion = 45;
        List<TestEvent> newEvents = ImmutableList.of(new TestEvent("test"));
        Operation operation = new OperationHandlerOperation(
                (x, y) -> newEvents,
                true,
                false,
                ExpectedVersion.any());
        AggregateProjection projection = mock(AggregateProjection.class);
        when(projection.empty()).thenReturn(new TestState(null));
        DefaultImmutableAggregate sourceAggregate =
                DefaultImmutableAggregate.createNew(projection, AGGREGATE_ID);
        when(repository.load(any())).thenReturn(sourceAggregate);
        when(repository.append(any(), any(), any(), any())).thenReturn(newVersion);
        DefaultCommand command = new DefaultCommand(repository, operation);
        command.setAggregateId(AGGREGATE_ID);
        command.setIdempotentCreate(true);
        CommandResult commandResult = command.run();

        ArgumentCaptor<List<TestEvent>> passedEvents
                = ArgumentCaptor.forClass((Class) List.class);
        verify(repository).append(eq(AGGREGATE_ID), passedEvents.capture(), any(), any());
        Assertions
                .assertThat(passedEvents.getValue())
                .contains(newEvents.toArray(new TestEvent[0]));

        Assert.assertEquals((long) newVersion, (long) commandResult.getNewVersion());
        Assertions
                .assertThat((List<TestEvent>) commandResult.getEvents())
                .contains(newEvents.toArray(new TestEvent[0]));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void idempotentCreateWorksIfConflictOnSave() {
        Operation operation = new OperationHandlerOperation(
                (x, y) -> ImmutableList.of("hello"),
                false,
                false,
                ExpectedVersion.notCreated());
        when(repository.load(any()))
                .thenReturn(DefaultImmutableAggregate.fromExisting(
                        mock(AggregateProjection.class),
                        AGGREGATE_ID,
                        42,
                        new TestState("test")));
        DefaultCommand command = new DefaultCommand(repository, operation);
        command.setAggregateId(AGGREGATE_ID);
        command.setIdempotentCreate(true);
        command.setAtomic(false);
        when(repository.append(any(), any(), any(), any()))
                .thenThrow(new UnexpectedVersionException(42, ExpectedVersion.notCreated()));
        CommandResult commandResult = command.run();

        Assert.assertTrue(commandResult.getEvents().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aggregateReadWhenStateRequired() {
        Operation operation = new OperationHandlerOperation(
                (x, y) -> ImmutableList.of(new TestEvent("test")),
                true,
                false,
                ExpectedVersion.any());

        DefaultCommand command = new DefaultCommand(repository, operation);
        when(repository.load(any()))
                .thenReturn(DefaultImmutableAggregate.fromExisting(
                        mock(AggregateProjection.class),
                        AGGREGATE_ID,
                        42,
                        new TestState("test")));
        command.setAggregateId(AGGREGATE_ID);
        command.run();

        verify(repository).load(AGGREGATE_ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void newEventsWrittenAndReturned() {
        int newVersion = 45;
        List<TestEvent> newEvents = ImmutableList.of(new TestEvent("test"));
        Operation operation = new OperationHandlerOperation(
                (x, y) -> newEvents,
                true,
                false,
                ExpectedVersion.any());

        DefaultCommand command = new DefaultCommand(repository, operation);
        when(repository.load(any()))
                .thenReturn(DefaultImmutableAggregate.fromExisting(
                        mock(AggregateProjection.class),
                        AGGREGATE_ID,
                        42,
                        new TestState("test")));
        when(repository.append(any(), any(), any(), any())).thenReturn(newVersion);

        command.setAggregateId(AGGREGATE_ID);
        CommandResult commandResult = command.run();

        ArgumentCaptor<List<TestEvent>> passedEvents
                = ArgumentCaptor.forClass((Class) List.class);
        verify(repository).append(eq(AGGREGATE_ID), passedEvents.capture(), any(), any());
        Assertions
                .assertThat(passedEvents.getValue())
                .contains(newEvents.toArray(new TestEvent[0]));

        Assert.assertEquals((long) newVersion, (long) commandResult.getNewVersion());
        Assertions
                .assertThat((List<TestEvent>) commandResult.getEvents())
                .contains(newEvents.toArray(new TestEvent[0]));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void metadataTakesPriorityOverMetadataDecorators() {
        int newVersion = 45;
        List<TestEvent> newEvents = ImmutableList.of(new TestEvent("test"));
        Operation operation = new OperationHandlerOperation(
                (x, y) -> newEvents,
                true,
                false,
                ExpectedVersion.any());

        DefaultCommand command = new DefaultCommand(repository, operation);
        command.addMetadata(ImmutableMap.of("key2", "value2direct"));
        command.addMetadataDecorator(new MetadataDecorator() {
            @Override
            public Map<String, String> getMetadata() {
                return ImmutableMap.of("key1", "value1", "key2", "value2decorator");
            }
        });

        when(repository.load(any()))
                .thenReturn(DefaultImmutableAggregate.fromExisting(
                        mock(AggregateProjection.class),
                        AGGREGATE_ID,
                        42,
                        new TestState("test")));
        when(repository.append(any(), any(), any(), any())).thenReturn(newVersion);

        command.setAggregateId(AGGREGATE_ID);
        command.run();

        ArgumentCaptor<Map<String, String>> passedMetadata
                = ArgumentCaptor.forClass((Class) Map.class);
        verify(repository).append(eq(AGGREGATE_ID), any(), any(), passedMetadata.capture());
        Assert.assertEquals(
                "value1",
                passedMetadata.getValue().get("key1"));
        Assert.assertEquals(
                "value2direct",
                passedMetadata.getValue().get("key2"));
    }
}
