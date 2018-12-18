package org.elder.sourcerer2.dbstore

import org.elder.sourcerer2.StreamId

interface DbstoreEventStore {
    /**
     * Reads events from a given particular point in time (version) across all of the events in this repository.
     * @param shardRange the hash range describing a logical shard to read from. This must always be specified,
     * to read from all events, specify the range describing all possible hash values.
     */
    fun readRepositoryEvents(
            repositoryInfo: DbstoreRepositoryInfo<*>,
            fromVersion: DbstoreRepositoryVersion? = null,
            shardRange: DbstoreShardHashRange,
            maxEvents: Int = 4096
    ): List<DbstoreEventRow>

    /**
     * Reads events from a given particular point in time (version) in an individual event stream.
     */
    fun readStreamEvents(
            repositoryInfo: DbstoreRepositoryInfo<*>,
            streamId: StreamId,
            fromVersion: DbstoreStreamVersion? = null,
            maxEvents: Int = 4096
    ): List<DbstoreEventRow>

    /**
     * Appends events to an individual event stream, with atomic guarantees if requested.
     * @throws DbstoreUnexpectedVersionException if the current version of a stream does not match
     * the expected requirements.
     */
    fun appendStreamEvents(
            repositoryInfo: DbstoreRepositoryInfo<*>,
            streamId: StreamId,
            expectExisting: Boolean?,
            expectVersion: DbstoreStreamVersion?,
            events: List<DbstoreEventData>
    ): DbstoreStreamVersion
}
