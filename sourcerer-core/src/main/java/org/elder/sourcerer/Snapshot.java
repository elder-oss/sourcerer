package org.elder.sourcerer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The current state of an aggregate at some point in time.
 */
public class Snapshot<TState> {
    private final TState state;
    private final Integer streamVersion;

    @JsonCreator
    public Snapshot(
            @JsonProperty("state") final TState state,
            @JsonProperty("streamVersion") final Integer streamVersion) {
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
