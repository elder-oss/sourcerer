package org.elder.sourcerer2

import com.google.common.collect.ImmutableList

data class RepositoryReadResult<T>(
        /**
         * The events read. Note that there may be fewer events here than requested, either because
         * the storage engine has limited the number of events that can be read in one go, or
         * because it is the end of the stream (as we know it). Use isEndOfStream to query which.
         */
        val events: ImmutableList<EventRecord<T>>,

        /**
         * The position to use to read more events in the same stream following those returned
         * already. This will be valid even if it is the end of the stream, and can be use to
         * query for whether or not someone else has added events at a later stage.
         */
        val nextVersion: RepositoryVersion,

        /**
         * True if this is (currently) representing the last events of the stream.
         */
        val isEndOfStream: Boolean
)
