package org.elder.sourcerer2;

import org.elder.sourcerer2.functions.AppendHandler;
import org.elder.sourcerer2.functions.AppendHandlerSingle;
import org.elder.sourcerer2.functions.ParameterizedAppendHandler;
import org.elder.sourcerer2.functions.ParameterizedAppendHandlerSingle;
import org.elder.sourcerer2.functions.ParameterizedPojoUpdateHandler;
import org.elder.sourcerer2.functions.ParameterizedPojoUpdateHandlerSingle;
import org.elder.sourcerer2.functions.ParameterizedUpdateHandler;
import org.elder.sourcerer2.functions.ParameterizedUpdateHandlerSingle;
import org.elder.sourcerer2.functions.ParameterizedUpdateHandlerAggregate;
import org.elder.sourcerer2.functions.PojoUpdateHandler;
import org.elder.sourcerer2.functions.PojoUpdateHandlerSingle;
import org.elder.sourcerer2.functions.UpdateHandler;
import org.elder.sourcerer2.functions.UpdateHandlerSingle;
import org.elder.sourcerer2.functions.UpdateHandlerAggregate;
import org.junit.Test;

import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class ImmutableAggregateApplyResolutionTest {
    private static class State {
    }

    private static class Event {
    }

    private static class Params {
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAppendOverloadWorks() {
        ImmutableAggregate<State, Event> aggregate = mock(ImmutableAggregate.class);
        aggregate.apply(this::append);
        verify(aggregate, times(1)).apply(any(AppendHandler.class));
        verifyNoMoreInteractions(aggregate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAppendSingleOverloadWorks() {
        ImmutableAggregate<State, Event> aggregate = mock(ImmutableAggregate.class);
        aggregate.apply(this::appendSingle);
        verify(aggregate, times(1)).apply(any(AppendHandlerSingle.class));
        verifyNoMoreInteractions(aggregate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAppendParamsOverloadWorks() {
        ImmutableAggregate<State, Event> aggregate = mock(ImmutableAggregate.class);
        aggregate.apply(this::appendParams, new Params());
        verify(aggregate, times(1)).apply(
                any(ParameterizedAppendHandler.class),
                any());
        verifyNoMoreInteractions(aggregate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAppendParamsSingleWorks() {
        ImmutableAggregate<State, Event> aggregate = mock(ImmutableAggregate.class);
        aggregate.apply(this::appendParamsSingle, new Params());
        verify(aggregate, times(1)).apply(
                any(ParameterizedAppendHandlerSingle.class),
                any());
        verifyNoMoreInteractions(aggregate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateOverloadWorks() {
        ImmutableAggregate<State, Event> aggregate = mock(ImmutableAggregate.class);
        aggregate.apply(this::update);
        verify(aggregate, times(1)).apply(any(UpdateHandler.class));
        verifyNoMoreInteractions(aggregate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateSingleOverloadWorks() {
        ImmutableAggregate<State, Event> aggregate = mock(ImmutableAggregate.class);
        aggregate.apply(this::updateSingle);
        verify(aggregate, times(1)).apply(any(UpdateHandlerSingle.class));
        verifyNoMoreInteractions(aggregate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateStateOverloadWorks() {
        ImmutableAggregate<State, Event> aggregate = mock(ImmutableAggregate.class);
        aggregate.apply(this::updateState);
        verify(aggregate, times(1)).apply(any(UpdateHandlerAggregate.class));
        verifyNoMoreInteractions(aggregate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdatePojoOverloadWorks() {
        ImmutableAggregate<State, Event> aggregate = mock(ImmutableAggregate.class);
        aggregate.apply(this::updatePojo);
        verify(aggregate, times(1)).apply(any(PojoUpdateHandler.class));
        verifyNoMoreInteractions(aggregate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdatePojoSingleOverloadWorks() {
        ImmutableAggregate<State, Event> aggregate = mock(ImmutableAggregate.class);
        aggregate.apply(this::updatePojoSingle);
        verify(aggregate, times(1)).apply(any(PojoUpdateHandlerSingle.class));
        verifyNoMoreInteractions(aggregate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParameterizedUpdateOverloadWorks() {
        ImmutableAggregate<State, Event> aggregate = mock(ImmutableAggregate.class);
        aggregate.apply(this::updateParams, new Params());
        verify(aggregate, times(1)).apply(
                any(ParameterizedUpdateHandler.class),
                any());
        verifyNoMoreInteractions(aggregate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParameterizedUpdateSingleOverloadWorks() {
        ImmutableAggregate<State, Event> aggregate = mock(ImmutableAggregate.class);
        aggregate.apply(this::updateParamsSingle, new Params());
        verify(aggregate, times(1)).apply(
                any(ParameterizedUpdateHandlerSingle.class),
                any());
        verifyNoMoreInteractions(aggregate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParameterizedUpdateStateOverloadWorks() {
        ImmutableAggregate<State, Event> aggregate = mock(ImmutableAggregate.class);
        aggregate.apply(this::updateParamsState, new Params());
        verify(aggregate, times(1)).apply(
                any(ParameterizedUpdateHandlerAggregate.class),
                any());
        verifyNoMoreInteractions(aggregate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParameterizedUpdatePojoOverloadWorks() {
        ImmutableAggregate<State, Event> aggregate = mock(ImmutableAggregate.class);
        aggregate.apply(this::updateParamsPojo, new Params());
        verify(aggregate, times(1)).apply(
                any(ParameterizedPojoUpdateHandler.class),
                any());
        verifyNoMoreInteractions(aggregate);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParameterizedUpdatePojoSingleOverloadWorks() {
        ImmutableAggregate<State, Event> aggregate = mock(ImmutableAggregate.class);
        aggregate.apply(this::updateParamsPojoSingle, new Params());
        verify(aggregate, times(1)).apply(
                any(ParameterizedPojoUpdateHandlerSingle.class),
                any());
        verifyNoMoreInteractions(aggregate);
    }

    private List<Event> append() {
        return null;
    }

    private Event appendSingle() {
        return null;
    }

    private List<Event> appendParams(final Params params) {
        return null;
    }

    private Event appendParamsSingle(final Params params) {
        return null;
    }

    private List<Event> update(final ImmutableAggregate<State, Event> aggregate) {
        return null;
    }

    private Event updateSingle(final ImmutableAggregate<State, Event> aggregate) {
        return null;
    }

    private ImmutableAggregate<State, Event> updateState(
            final ImmutableAggregate<State, Event> aggregate) {
        return null;
    }

    private List<Event> updatePojo(final State state) {
        return null;
    }

    private Event updatePojoSingle(final State state) {
        return null;
    }

    private List<Event> updateParams(
            final ImmutableAggregate<State, Event> aggregate,
            final Params params) {
        return null;
    }

    private Event updateParamsSingle(
            final ImmutableAggregate<State, Event> aggregate,
            final Params params) {
        return null;
    }

    private ImmutableAggregate<State, Event> updateParamsState(
            final ImmutableAggregate<State, Event> aggregate,
            final Params params) {
        return null;
    }

    private List<Event> updateParamsPojo(final State state, final Params params) {
        return null;
    }

    private Event updateParamsPojoSingle(final State state, final Params params) {
        return null;
    }
}
