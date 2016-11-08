package org.elder.sourcerer;

public final class TestEvent {
    private final String value;

    public TestEvent(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "TestEvent{" +
                "value='" + value + '\'' +
                '}';
    }
}
