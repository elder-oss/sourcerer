package org.elder.sourcerer.subscription;

import org.elder.sourcerer.ImmutableAggregate;
import org.elder.sourcerer.Operation;
import org.elder.sourcerer.OperationHandler;
import org.elder.sourcerer.OperationHandlerOperation;
import org.elder.sourcerer.Operations;
import org.elder.sourcerer.functions.AppendHandler;
import org.elder.sourcerer.functions.AppendHandlerSingle;
import org.elder.sourcerer.functions.ConstructorHandler;
import org.elder.sourcerer.functions.ConstructorHandlerSingle;
import org.elder.sourcerer.functions.ParameterizedAppendHandler;
import org.elder.sourcerer.functions.ParameterizedAppendHandlerSingle;
import org.elder.sourcerer.functions.ParameterizedConstructorHandler;
import org.elder.sourcerer.functions.ParameterizedConstructorHandlerSingle;
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
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Make sure that our Java 8 function overloads resolve to the expected functional interface, and
 * we get no compile time ambiguities.
 */
public class OperationsFunctionOverloadTest {
    private static class Event {
    }

    private static class Params {
    }

    private static class State {
    }

    @Test
    public void testAppendResolved() {
        Operation<Object, Object, Event> operation = Operations.appendOf(this::append);
        assertHandlerType(operation, AppendHandler.class);
    }

    @Test
    public void testAppendSingleResolved() {
        Operation<Object, Object, Event> operation = Operations.appendOf(this::appendSingle);
        assertHandlerType(operation, AppendHandlerSingle.class);
    }

    @Test
    public void testParameterizedAppendResolved() {
        Operation<Object, Params, Event> operation =
                Operations.appendOf(this::parameterizedAppend);
        assertHandlerType(operation, ParameterizedAppendHandler.class);
    }

    @Test
    public void testParameterizedAppendSingleResolved() {
        Operation<Object, Params, Event> operation =
                Operations.appendOf(this::parameterizedAppendSingle);
        assertHandlerType(operation, ParameterizedAppendHandlerSingle.class);
    }

    @Test
    public void testConstructorResolved() {
        Operation<Object, Object, Event> operation = Operations.constructorOf(this::constructor);
        assertHandlerType(operation, ConstructorHandler.class);
    }

    @Test
    public void testConstructorSingleResolved() {
        Operation<Object, Object, Event> operation =
                Operations.constructorOf(this::constructorSingle);
        assertHandlerType(operation, ConstructorHandlerSingle.class);
    }

    @Test
    public void testParameterizedConstructorResolved() {
        Operation<Object, Params, Event> operation =
                Operations.constructorOf(this::parameterizedConstructor);
        assertHandlerType(operation, ParameterizedConstructorHandler.class);
    }

    @Test
    public void testParameterizedConstructorSingleResolved() {
        Operation<Object, Params, Event> operation =
                Operations.constructorOf(this::parameterizedConstructorSingle);
        assertHandlerType(operation, ParameterizedConstructorHandlerSingle.class);
    }

    @Test
    public void testUpdateResolved() {
        Operation<State, Object, Event> operation =
                Operations.updateOf(this::update);
        this.assertHandlerType(operation, UpdateHandler.class);
    }

    @Test
    public void testUpdateSingleResolved() {
        Operation<State, Object, Event> operation =
                Operations.updateOf(this::updateSingle);
        this.assertHandlerType(operation, UpdateHandlerSingle.class);
    }

    @Test
    public void testUpdateStateResolved() {
        Operation<State, Object, Event> operation =
                Operations.updateOf(this::updateState);
        this.assertHandlerType(operation, UpdateHandlerState.class);
    }

    @Test
    public void testParameterizedUpdateResolved() {
        Operation<State, Params, Event> operation =
                Operations.updateOf(this::parameterizedUpdate);
        this.assertHandlerType(operation, ParameterizedUpdateHandler.class);
    }

    @Test
    public void testParameterizedUpdateSingleResolved() {
        Operation<State, Params, Event> operation =
                Operations.updateOf(this::parameterizedUpdateSingle);
        this.assertHandlerType(operation, ParameterizedUpdateHandlerSingle.class);
    }

    @Test
    public void testParameterizedUpdateStateResolved() {
        Operation<State, Params, Event> operation =
                Operations.updateOf(this::parameterizedUpdateState);
        this.assertHandlerType(operation, ParameterizedUpdateHandlerState.class);
    }

    @Test
    public void testUpdatePojoResolved() {
        Operation<State, Object, Event> operation =
                Operations.updateOf(this::updatePojo);
        this.assertHandlerType(operation, PojoUpdateHandler.class);
    }

    @Test
    public void testUpdateSinglePojoResolved() {
        Operation<State, Object, Event> operation =
                Operations.updateOf(this::updateSinglePojo);
        this.assertHandlerType(operation, PojoUpdateHandlerSingle.class);
    }

    @Test
    public void testParameterizedUpdatePojoResolved() {
        Operation<State, Params, Event> operation =
                Operations.updateOf(this::parameterizedUpdatePojo);
        this.assertHandlerType(operation, ParameterizedPojoUpdateHandler.class);
    }

    @Test
    public void testParameterizedUpdateSinglePojoResolved() {
        Operation<State, Params, Event> operation =
                Operations.updateOf(this::parameterizedUpdateSinglePojo);
        this.assertHandlerType(operation, ParameterizedPojoUpdateHandlerSingle.class);
    }

    private <TState, TParams, TEvent> void assertHandlerType(
            final Operation<TState, TParams, TEvent> operation,
            final Class<? extends OperationHandler> expectedHandlerClass) {
        Assert.assertTrue(
                expectedHandlerClass.isAssignableFrom(
                        ((OperationHandlerOperation) operation).handler().getClass()));
    }

    private List<Event> append() {
        return null;
    }

    private Event appendSingle() {
        return null;
    }

    private List<Event> parameterizedAppend(final Params params) {
        return null;
    }

    private Event parameterizedAppendSingle(final Params params) {
        return null;
    }

    private List<Event> constructor() {
        return null;
    }

    private Event constructorSingle() {
        return null;
    }

    private List<Event> parameterizedConstructor(final Params params) {
        return null;
    }

    private Event parameterizedConstructorSingle(final Params params) {
        return null;
    }

    private List<Event> update(final ImmutableAggregate<State, Event> state) {
        return null;
    }

    private Event updateSingle(final ImmutableAggregate<State, Event> state) {
        return null;
    }

    private ImmutableAggregate<State, Event> updateState(
            final ImmutableAggregate<State, Event> state) {
        return null;
    }

    private List<Event> parameterizedUpdate(
            final ImmutableAggregate<State, Event> state, final Params params) {
        return null;
    }

    private Event parameterizedUpdateSingle(
            final ImmutableAggregate<State, Event> state, final Params params) {
        return null;
    }

    private ImmutableAggregate<State, Event> parameterizedUpdateState(
            final ImmutableAggregate<State, Event> state, final Params params) {
        return null;
    }

    private List<Event> updatePojo(final State state) {
        return null;
    }

    private Event updateSinglePojo(final State state) {
        return null;
    }

    private List<Event> parameterizedUpdatePojo(final State state, final Params params) {
        return null;
    }

    private Event parameterizedUpdateSinglePojo(final State state, final Params params) {
        return null;
    }
}
