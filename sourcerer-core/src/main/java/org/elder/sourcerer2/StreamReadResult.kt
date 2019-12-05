package org.elder.sourcerer2

import com.google.common.collect.ImmutableList

data class StreamReadResult<T>(
        /**
         * The events read. Note that there may be fewer events here than requested, either because
         * the storage engine has limited the number of events that can be read in one go, or
         * because it is the end of the stream (as we know it). Use isEndOfStream to query which.
         */
        val events: ImmutableList<EventRecord<T>>,

        /**
         * The position in the stream marked by the last event read. This can be used to either read
         * more events in the stream, or to ensure that events are added atomically later on by
         * asserting that the last event is still at this position.
         */
        val version: StreamVersion,

        /**
         * True if this is (currently) representing the last events of the stream.
         */
        val isEndOfStream: Boolean
)
