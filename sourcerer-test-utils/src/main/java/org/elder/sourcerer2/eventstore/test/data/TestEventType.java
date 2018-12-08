package org.elder.sourcerer2.eventstore.test.data;

import org.elder.sourcerer2.EventType;

/**
 * Event type used for integration tests.
 */
@EventType(repositoryName = "testrepo")
public class TestEventType {
    private String value;

    private TestEventType() {
    }

    public TestEventType(final String value) {
        this.value = value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
