package org.elder.sourcerer2;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.elder.sourcerer2.utils.RetryPolicy;
import org.elder.sourcerer2.exceptions.InvalidCommandException;
import org.elder.sourcerer2.exceptions.UnexpectedVersionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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
    private static final StreamId AGGREGATE_ID = StreamId.ofString("42");
    private final RetryPolicy retryPolicy = RetryPolicy.noRetries();
    private AggregateRepository repository;

    @Before
    public void setUp() {
        this.repository = mock(AggregateRepository.class);
    }

    @Test(expected = InvalidCommandException.class)
    @SuppressWarnings("unchecked")
    public void conflictingVersionsGiveInvalidCommand() {
        ExpectedVersion expectedVersion = ExpectedVersion.exactly(StreamVersion.ofInt(42));
        Operation operation = new OperationHandlerOperation(
                (x, y) -> null,
                true,
                false,
                expectedVersion);
        DefaultCommand command = new DefaultCommand(repository, operation);
        command.setExpectedVersion(ExpectedVersion.notCreated());
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
                        StreamVersion.ofInt(42),
                        new TestState("test")));
        DefaultCommand command = new DefaultCommand(repository, operation, retryPolicy);
        command.setAggregateId(AGGREGATE_ID);
        command.setIdempotentCreate(true);
        CommandResult commandResult = command.run();

        Assert.assertTrue(commandResult.getEvents().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void idempotentCreateSuccessfulIfNotPresent() {
        StreamVersion newVersion = StreamVersion.ofInt(45);
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
        DefaultCommand command = new DefaultCommand(repository, operation, retryPolicy);
        command.setAggregateId(AGGREGATE_ID);
        command.setIdempotentCreate(true);
        CommandResult commandResult = command.run();

        ArgumentCaptor<List<TestEvent>> passedEvents
                = ArgumentCaptor.forClass((Class) List.class);
        verify(repository).append(eq(AGGREGATE_ID), passedEvents.capture(), any(), any());
        Assert.assertThat(
                passedEvents.getValue(),
                org.hamcrest.Matchers.contains(newEvents.toArray()));

        Assert.assertEquals(newVersion, commandResult.getNewVersion());
        Assert.assertThat(
                (List<TestEvent>) commandResult.getEvents(),
                org.hamcrest.Matchers.contains(newEvents.toArray()));
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
                        StreamVersion.ofInt(42),
                        new TestState("test")));
        DefaultCommand command = new DefaultCommand(repository, operation, retryPolicy);
        command.setAggregateId(AGGREGATE_ID);
        command.setIdempotentCreate(true);
        command.setAtomic(false);
        when(repository.append(any(), any(), any(), any()))
                .thenThrow(new UnexpectedVersionException(
                        StreamVersion.ofInt(42),
                        ExpectedVersion.notCreated()));
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

        DefaultCommand command = new DefaultCommand(repository, operation, retryPolicy);
        when(repository.load(any()))
                .thenReturn(DefaultImmutableAggregate.fromExisting(
                        mock(AggregateProjection.class),
                        AGGREGATE_ID,
                        StreamVersion.ofInt(42),
                        new TestState("test")));
        when(repository.append(any(), any(), any(), any()))
                .thenReturn(StreamVersion.ofInt(42));
        command.setAggregateId(AGGREGATE_ID);
        command.run();

        verify(repository).load(AGGREGATE_ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void newEventsWrittenAndReturned() {
        StreamVersion newVersion = StreamVersion.ofInt(45);
        List<TestEvent> newEvents = ImmutableList.of(new TestEvent("test"));
        Operation operation = new OperationHandlerOperation(
                (x, y) -> newEvents,
                true,
                false,
                ExpectedVersion.any());

        DefaultCommand command = new DefaultCommand(repository, operation, retryPolicy);
        when(repository.load(any()))
                .thenReturn(DefaultImmutableAggregate.fromExisting(
                        mock(AggregateProjection.class),
                        AGGREGATE_ID,
                        StreamVersion.ofInt(42),
                        new TestState("test")));
        when(repository.append(any(), any(), any(), any())).thenReturn(newVersion);

        command.setAggregateId(AGGREGATE_ID);
        CommandResult commandResult = command.run();

        ArgumentCaptor<List<TestEvent>> passedEvents
                = ArgumentCaptor.forClass((Class) List.class);
        verify(repository).append(eq(AGGREGATE_ID), passedEvents.capture(), any(), any());
        Assert.assertThat(
                passedEvents.getValue(),
                org.hamcrest.Matchers.contains(newEvents.toArray()));

        Assert.assertEquals(newVersion, commandResult.getNewVersion());
        Assert.assertThat(
                (List<TestEvent>) commandResult.getEvents(),
                org.hamcrest.Matchers.contains(newEvents.toArray()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void metadataTakesPriorityOverMetadataDecorators() {
        StreamVersion newVersion = StreamVersion.ofInt(45);
        List<TestEvent> newEvents = ImmutableList.of(new TestEvent("test"));
        Operation operation = new OperationHandlerOperation(
                (x, y) -> newEvents,
                true,
                false,
                ExpectedVersion.any());

        DefaultCommand command = new DefaultCommand(repository, operation, retryPolicy);
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
                        StreamVersion.ofInt(42),
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
