package org.elder.sourcerer2;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * These tests each assert in both directions, merge first with the second, and the second with
 * the first.
 */
public class ExpectedVersionMergeTest {

    // Any

    @Test
    public void mergeAnyAndAny() {
        assertMerge(ExpectedVersion.any(), ExpectedVersion.any(), ExpectedVersion.any());
    }

    @Test
    public void mergeAnyAndNull() {
        assertMerge(ExpectedVersion.any(), null, ExpectedVersion.any());
    }

    @Test
    public void mergeAnyAndAnyExisting() {
        assertMerge(
                ExpectedVersion.any(),
                ExpectedVersion.anyExisting(),
                ExpectedVersion.anyExisting());
    }

    @Test
    public void mergeAnyAndExactly() {
        assertMerge(
                ExpectedVersion.any(),
                ExpectedVersion.exactly(StreamVersion.ofInt(3)),
                ExpectedVersion.exactly(StreamVersion.ofInt(3)));
    }

    @Test
    public void mergeAnyAndNotCreated() {
        assertMerge(
                ExpectedVersion.any(),
                ExpectedVersion.notCreated(),
                ExpectedVersion.notCreated());
    }

    // Any existing

    @Test
    public void mergeAnyExistingAndAnyExisting() {
        assertMerge(
                ExpectedVersion.anyExisting(),
                ExpectedVersion.anyExisting(),
                ExpectedVersion.anyExisting());
    }

    @Test
    public void mergeAnyExistingAndNull() {
        assertMerge(ExpectedVersion.anyExisting(), null, ExpectedVersion.anyExisting());
    }

    @Test
    public void mergeAnyExistingAndExactly() {
        assertMerge(
                ExpectedVersion.anyExisting(),
                ExpectedVersion.exactly(StreamVersion.ofInt(3)),
                ExpectedVersion.exactly(StreamVersion.ofInt(3)));
    }

    @Test
    public void mergeAnyExistingAndNotCreated() {
        assertMergeFail(ExpectedVersion.anyExisting(), ExpectedVersion.notCreated());
    }

    // Exactly

    @Test
    public void mergeExactlyAndEqualExactly() {
        assertMerge(
                ExpectedVersion.exactly(StreamVersion.ofInt(3)),
                ExpectedVersion.exactly(StreamVersion.ofInt(3)),
                ExpectedVersion.exactly(StreamVersion.ofInt(3)));
    }

    @Test
    public void mergeExactlyAndNull() {
        assertMerge(
                ExpectedVersion.exactly(StreamVersion.ofInt(3)),
                null,
                ExpectedVersion.exactly(StreamVersion.ofInt(3)));
    }

    @Test
    public void mergeExactlyAndDifferentExactly() {
        assertMergeFail(
                ExpectedVersion.exactly(StreamVersion.ofInt(3)),
                ExpectedVersion.exactly(StreamVersion.ofInt(4)));
    }

    @Test
    public void mergeExactlyAndNotCreated() {
        assertMergeFail(
                ExpectedVersion.exactly(StreamVersion.ofInt(3)),
                ExpectedVersion.notCreated());
    }

    // Not created

    @Test
    public void mergeNotCreatedAndNotCreated() {
        assertMerge(
                ExpectedVersion.notCreated(),
                ExpectedVersion.notCreated(),
                ExpectedVersion.notCreated());
    }

    @Test
    public void mergeNotCreatedAndNull() {
        assertMerge(ExpectedVersion.notCreated(), null, ExpectedVersion.notCreated());
    }

    private void assertMerge(
            final ExpectedVersion first,
            final ExpectedVersion second,
            final ExpectedVersion expected) {
        assertEqual(
                first + " merged with " + second,
                expected,
                ExpectedVersion.merge(first, second));
        assertEqual(
                second + " merged with " + first,
                expected,
                ExpectedVersion.merge(second, first));
    }

    private void assertMergeFail(final ExpectedVersion first, final ExpectedVersion second) {
        assertMergeFailOneDirection(first, second);
        assertMergeFailOneDirection(second, first);
    }

    private void assertEqual(
            final String msg,
            final ExpectedVersion actual,
            final ExpectedVersion expected) {
        assertEquals("Version: " + msg, expected, actual);
    }

    private void assertMergeFailOneDirection(
            final ExpectedVersion first,
            final ExpectedVersion second) {
        try {
            ExpectedVersion.merge(first, second);
            fail("Expected failure for merging " + first + " with " + second);
        } catch (RuntimeException ex) {
            // Success
        }
    }
}
