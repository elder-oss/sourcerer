package org.elder.sourcerer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * The current state of an aggregate at some point in time.
 */
public class Snapshot<TState> {
    private final TState state;
    private final Integer streamVersion;

    public Snapshot(
            final TState state,
            final Integer streamVersion) {
        this.state = state;
        this.streamVersion = streamVersion;
    }

    public TState getState() {
        return state;
    }

    public Integer getStreamVersion() {
        return streamVersion;
    }
}
