package org.elder.sourcerer;

import com.google.common.collect.ImmutableList;
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
                (x, y) -> null,
                false,
                false,
                ExpectedVersion.notCreated());
        when(repository.read(any()))
                .thenReturn(new AggregateRecord(AGGREGATE_ID, new TestState("test"), 42));
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
        when(repository.read(any())).thenReturn(new AggregateRecord("id", null, -1));
        when(repository.update(any(), any(), any(), any())).thenReturn(newVersion);
        DefaultCommand command = new DefaultCommand(repository, operation);
        command.setAggregateId(AGGREGATE_ID);
        command.setIdempotentCreate(true);
        CommandResult commandResult = command.run();

        ArgumentCaptor<List<TestEvent>> passedEvents
                = ArgumentCaptor.forClass((Class) List.class);
        verify(repository).update(eq(AGGREGATE_ID), passedEvents.capture(), any(), any());
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
        when(repository.read(any()))
                .thenReturn(new AggregateRecord(AGGREGATE_ID, new TestState("test"), 42));
        DefaultCommand command = new DefaultCommand(repository, operation);
        command.setAggregateId(AGGREGATE_ID);
        command.setIdempotentCreate(true);
        command.setAtomic(false);
        when(repository.update(any(), any(), any(), any()))
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
        when(repository.read(any()))
                .thenReturn(new AggregateRecord(AGGREGATE_ID, new TestState("test"), 42));
        command.setAggregateId(AGGREGATE_ID);
        command.run();

        verify(repository).read(AGGREGATE_ID);
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
        when(repository.read(any()))
                .thenReturn(new AggregateRecord(AGGREGATE_ID, new TestState("test"), 42));
        when(repository.update(any(), any(), any(), any())).thenReturn(newVersion);

        command.setAggregateId(AGGREGATE_ID);
        CommandResult commandResult = command.run();

        ArgumentCaptor<List<TestEvent>> passedEvents
                = ArgumentCaptor.forClass((Class) List.class);
        verify(repository).update(eq(AGGREGATE_ID), passedEvents.capture(), any(), any());
        Assert.assertThat(
                passedEvents.getValue(),
                org.hamcrest.Matchers.contains(newEvents.toArray()));

        Assert.assertEquals((long) newVersion, (long) commandResult.getNewVersion());
        Assert.assertThat(
                (List<TestEvent>) commandResult.getEvents(),
                org.hamcrest.Matchers.contains(newEvents.toArray()));
    }
}
