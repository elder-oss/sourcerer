package org.elder.sourcerer;

public final class TestState {
    private final String value;

    public TestState(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "TestState{" +
                "value='" + value + '\'' +
                '}';
    }
}
