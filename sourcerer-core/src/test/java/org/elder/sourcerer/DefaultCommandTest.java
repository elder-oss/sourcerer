package org.elder.sourcerer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.elder.sourcerer.exceptions.ConflictingExpectedVersionsException;
import org.elder.sourcerer.exceptions.InvalidCommandException;
import org.elder.sourcerer.exceptions.UnexpectedVersionException;
import org.elder.sourcerer.utils.RetryPolicy;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultCommandTest {
    private static final String AGGREGATE_ID = "42";
    private final RetryPolicy retryPolicy = RetryPolicy.noRetries();
    private AggregateRepository repository = mock(AggregateRepository.class);

    @Test(expected = InvalidCommandException.class)
    @SuppressWarnings("unchecked")
    public void conflictingVersionsGiveInvalidCommand() {

        try (var mocked = Mockito.mockStatic(ExpectedVersion.class)) {
            mocked.when(() -> ExpectedVersion.merge(any(), any()))
                    .thenThrow(new ConflictingExpectedVersionsException(
                            "error", null, null));

            Operation operation = new OperationHandlerOperation(
                    (x, y) -> null,
                    true,
                    false,
                    ExpectedVersion.exactly(42));
            DefaultCommand command = new DefaultCommand(repository, operation, retryPolicy);
            command.setAggregateId(AGGREGATE_ID);
            command.run();
        }
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
        DefaultCommand command = new DefaultCommand(repository, operation, retryPolicy);
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

        Assert.assertEquals((long) newVersion, (long) commandResult.getNewVersion());
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
                        42,
                        new TestState("test")));
        DefaultCommand command = new DefaultCommand(repository, operation, retryPolicy);
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

        DefaultCommand command = new DefaultCommand(repository, operation, retryPolicy);
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

        DefaultCommand command = new DefaultCommand(repository, operation, retryPolicy);
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
        Assert.assertThat(
                passedEvents.getValue(),
                org.hamcrest.Matchers.contains(newEvents.toArray()));

        Assert.assertEquals((long) newVersion, (long) commandResult.getNewVersion());
        Assert.assertThat(
                (List<TestEvent>) commandResult.getEvents(),
                org.hamcrest.Matchers.contains(newEvents.toArray()));
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
