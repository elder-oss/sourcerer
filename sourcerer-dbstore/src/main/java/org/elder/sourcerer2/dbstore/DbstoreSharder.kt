package org.elder.sourcerer2.dbstore

import org.elder.sourcerer2.RepositoryShard
import org.elder.sourcerer2.StreamHash

data class DbstoreShardHashRange(
        val fromHashInclusive: Int,
        val toHashExclusive: Int
) {
    companion object {
        val COMPLETE_RANGE = DbstoreShardHashRange(0, StreamHash.HASH_VALUES)
    }
}

object DbstoreSharder {
    /**
     * Get a range of all available hashes to use for sharding. This is calculated so that all ranges
     * are as evenly sized as possible but non overlapping (for the same value of totalShards).
     */
    fun getShardRange(shard: RepositoryShard): DbstoreShardHashRange {
        val from = shard.shardIndex.toLong() * RepositoryShard.MAX_SHARD_COUNT
        val to = from + RepositoryShard.MAX_SHARD_COUNT

        return DbstoreShardHashRange(
                fromHashInclusive = (from / shard.shardCount).toInt(),
                toHashExclusive = (to / shard.shardCount).toInt()
        )
    }
}
