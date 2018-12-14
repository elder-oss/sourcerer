package org.elder.sourcerer2.dbstore

import org.elder.sourcerer2.StreamId


interface DbstoreEventStore {
    /**
     * The number of independent shards that this repository supports. Each individual event stream will be
     * automatically assigned to a shard when the stream is first created in the range of [0,shards). The number of
     * shards can be configured to go up over time, but not down (or some events would be excluded). Note that as
     * shards are assigned when the stream is first created, increasing the number of shards at a later stage will not
     * re-balance old streams, it will only ensure that newly created streams are spread across all then available
     * shards.
     */
    val shards: Int

    /**
     * Reads events from a given particular point in time (version) across all of the events in this repository.
     * @param shard the shard to read from, if null, events will be read across all of the shards (in order).
     */
    fun readRepositoryEvents(
            category: String,
            shard: Int?,
            fromVersion: DbstoreRepositoryVersion? = null,
            maxEvents: Int = 4096
    ): List<DbstoreEventRow>

    /**
     * Reads events from a given particular point in time (version) in an individual event stream.
     */
    fun readStreamEvents(
            streamId: StreamId,
            category: String,
            fromVersion: DbstoreStreamVersion? = null,
            maxEvents: Int = 4096
    ): List<DbstoreEventRow>

    /**
     * Appends events to an individual event stream, with atomic guarantees if requested.
     * @throws DbstoreUnexpectedVersionException if the current version of a stream does not match
     * the expected requirements.
     */
    fun appendStreamEvents(
            streamId: StreamId,
            category: String,
            expectExisting: Boolean?,
            expectVersion: DbstoreStreamVersion?,
            events: List<DbstoreEventData>
    ): DbstoreStreamVersion
}
