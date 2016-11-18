package org.elder.sourcerer;

import java.util.Objects;

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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TestState testState = (TestState) o;
        return Objects.equals(value, testState.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
