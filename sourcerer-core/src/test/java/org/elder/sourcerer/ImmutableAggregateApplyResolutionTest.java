package org.elder.sourcerer;

import org.elder.sourcerer.functions.AppendHandler;
import org.elder.sourcerer.functions.AppendHandlerSingle;
import org.elder.sourcerer.functions.ParameterizedAppendHandler;
import org.elder.sourcerer.functions.ParameterizedAppendHandlerSingle;
import org.elder.sourcerer.functions.ParameterizedPojoUpdateHandler;
import org.elder.sourcerer.functions.ParameterizedPojoUpdateHandlerSingle;
import org.elder.sourcerer.functions.ParameterizedUpdateHandler;
import org.elder.sourcerer.functions.ParameterizedUpdateHandlerSingle;
import org.elder.sourcerer.functions.ParameterizedUpdateHandlerState;
import org.elder.sourcerer.functions.PojoUpdateHandler;
import org.elder.sourcerer.functions.PojoUpdateHandlerSingle;
import org.elder.sourcerer.functions.UpdateHandler;
import org.elder.sourcerer.functions.UpdateHandlerSingle;
import org.elder.sourcerer.functions.UpdateHandlerState;
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
        verify(aggregate, times(1)).apply(any(UpdateHandlerState.class));
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
                any(ParameterizedUpdateHandlerState.class),
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

    private List<Event> appendParams(Params params) {
        return null;
    }

    private Event appendParamsSingle(Params params) {
        return null;
    }

    private List<Event> update(ImmutableAggregate<State, Event> aggregate) {
        return null;
    }

    private Event updateSingle(ImmutableAggregate<State, Event> aggregate) {
        return null;
    }

    private ImmutableAggregate<State, Event> updateState(
            ImmutableAggregate<State, Event> aggregate) {
        return null;
    }

    private List<Event> updatePojo(State state) {
        return null;
    }

    private Event updatePojoSingle(State state) {
        return null;
    }

    private List<Event> updateParams(ImmutableAggregate<State, Event> aggregate, Params params) {
        return null;
    }

    private Event updateParamsSingle(ImmutableAggregate<State, Event> aggregate, Params params) {
        return null;
    }

    private ImmutableAggregate<State, Event> updateParamsState(
            ImmutableAggregate<State, Event> aggregate,
            Params params) {
        return null;
    }

    private List<Event> updateParamsPojo(State state, Params params) {
        return null;
    }

    private Event updateParamsPojoSingle(State state, Params params) {
        return null;
    }
}