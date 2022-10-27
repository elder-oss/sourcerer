package org.elder.sourcerer.subscription;

import org.junit.Assert;
import org.junit.Test;

public class SubscriptionWorkerTest {

    @Test
    public void testIsCausedBy() {
        final IllegalArgumentException ex = new IllegalArgumentException(
                "Illegal argument exception",
                new TypeNotPresentException(
                        "Type not present exception",
                        new ClassNotFoundException("Class not found exception")
                )
        );
        Assert.assertFalse(SubscriptionWorker.isCausedBy(null, ClassNotFoundException.class));
        Assert.assertTrue(SubscriptionWorker.isCausedBy(ex, IllegalArgumentException.class));
        Assert.assertTrue(SubscriptionWorker.isCausedBy(ex, TypeNotPresentException.class));
        Assert.assertTrue(SubscriptionWorker.isCausedBy(ex, ClassNotFoundException.class));
        Assert.assertFalse(SubscriptionWorker.isCausedBy(ex, NullPointerException.class));
    }
}
