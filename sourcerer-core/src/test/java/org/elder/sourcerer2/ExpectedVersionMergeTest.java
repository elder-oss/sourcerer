package org.elder.sourcerer2;

import org.junit.Test;

import static org.elder.sourcerer2.ExpectedVersion.any;
import static org.elder.sourcerer2.ExpectedVersion.anyExisting;
import static org.elder.sourcerer2.ExpectedVersion.exactly;
import static org.elder.sourcerer2.ExpectedVersion.merge;
import static org.elder.sourcerer2.ExpectedVersion.notCreated;

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
        assertMerge(any(), any(), any());
    }

    @Test
    public void mergeAnyAndNull() {
        assertMerge(any(), null, any());
    }

    @Test
    public void mergeAnyAndAnyExisting() {
        assertMerge(any(), anyExisting(), anyExisting());
    }

    @Test
    public void mergeAnyAndExactly() {
        assertMerge(any(), exactly(3), exactly(3));
    }

    @Test
    public void mergeAnyAndNotCreated() {
        assertMerge(any(), notCreated(), notCreated());
    }

    // Any existing

    @Test
    public void mergeAnyExistingAndAnyExisting() {
        assertMerge(anyExisting(), anyExisting(), anyExisting());
    }

    @Test
    public void mergeAnyExistingAndNull() {
        assertMerge(anyExisting(), null, anyExisting());
    }

    @Test
    public void mergeAnyExistingAndExactly() {
        assertMerge(anyExisting(), exactly(3), exactly(3));
    }

    @Test
    public void mergeAnyExistingAndNotCreated() {
        assertMergeFail(anyExisting(), notCreated());
    }

    // Exactly

    @Test
    public void mergeExactlyAndEqualExactly() {
        assertMerge(exactly(3), exactly(3), exactly(3));
    }

    @Test
    public void mergeExactlyAndNull() {
        assertMerge(exactly(3), null, exactly(3));
    }

    @Test
    public void mergeExactlyAndDifferentExactly() {
        assertMergeFail(exactly(3), exactly(4));
    }

    @Test
    public void mergeExactlyAndNotCreated() {
        assertMergeFail(exactly(3), notCreated());
    }

    // Not created

    @Test
    public void mergeNotCreatedAndNotCreated() {
        assertMerge(notCreated(), notCreated(), notCreated());
    }

    @Test
    public void mergeNotCreatedAndNull() {
        assertMerge(notCreated(), null, notCreated());
    }

    private void assertMerge(
            final ExpectedVersion first,
            final ExpectedVersion second,
            final ExpectedVersion expected) {
        assertEqual(first + " merged with " + second, expected, merge(first, second));
        assertEqual(second + " merged with " + first, expected, merge(second, first));
    }

    private void assertMergeFail(final ExpectedVersion first, final ExpectedVersion second) {
        assertMergeFailOneDirection(first, second);
        assertMergeFailOneDirection(second, first);
    }

    private void assertEqual(
            final String msg,
            final ExpectedVersion actual,
            final ExpectedVersion expected) {
        assertEquals(
                "Version: " + msg,
                expected.getExpectedVersion(),
                actual.getExpectedVersion());
        assertEquals("Type: " + msg, expected.getType(), actual.getType());
    }

    private void assertMergeFailOneDirection(
            final ExpectedVersion first,
            final ExpectedVersion second) {
        try {
            merge(first, second);
            fail("Expected failure for merging " + first + " with " + second);
        } catch (RuntimeException ex) {
            // Success
        }
    }
}
