package org.elder.sourcerer2.dbstore

import com.google.common.hash.Hashing
import org.elder.sourcerer2.StreamId
import java.lang.IllegalArgumentException
import java.nio.charset.StandardCharsets

// Select an arbitrary but large number of unique shard values to use, this is
// ~10^9 and can safely be represented as a signed 32 bit integer value
private const val HASH_VALUES = 0x40000000

// Arbitrary "large" value, if we have more shards than this, something is weird!
private const val MAX_SHARDS = 0x8000

data class DbstoreShardHashRange(
        val fromHashInclusive: Int,
        val toHashExclusive: Int
) {
    companion object {
        val COMPLETE_RANGE = DbstoreShardHashRange(0, HASH_VALUES)
    }
}

object DbstoreSharder {
    private val shardHashFunction = Hashing.murmur3_128()

    fun getStreamHash(
            repositoryInfo: DbstoreRepositoryInfo<*>,
            streamId: StreamId
    ): Int {
        return shardHashFunction.newHasher()
                .putString(repositoryInfo.repository, StandardCharsets.UTF_8)
                .putString(streamId.identifier, StandardCharsets.UTF_8)
                .hash()
                .asInt()
                .getHash()
    }

    /**
     * Get a range of all available hashes to use for sharding. This is calculated so that all ranges
     * are as evenly sized as possible but non overlapping (for the same value of totalShards).
     * @param shardIndex 0 based index for a logical shard in the range [0, totalShards).
     * @param totalShards the total shards to break up the hash space in
     */
    fun getShardRange(shardIndex: Int, totalShards: Int): DbstoreShardHashRange {
        if (totalShards < 0) {
            throw IllegalArgumentException("Total shards must be greater than 0")
        }
        if (totalShards >= MAX_SHARDS) {
            throw IllegalArgumentException("Total shards must be less than $MAX_SHARDS")
        }
        if (shardIndex < 0) {
            throw IllegalArgumentException("Shard index must be greater than 0")
        }
        if (shardIndex >= totalShards) {
            throw IllegalArgumentException("Shard index must be less than tht total number of shards")
        }

        val from = shardIndex.toLong() * MAX_SHARDS
        val to = from + MAX_SHARDS

        return DbstoreShardHashRange(
                fromHashInclusive = (from / totalShards).toInt(),
                toHashExclusive = (to / totalShards).toInt()
        )
    }

    private fun Int.getHash(): Int {
        val remainder = this % HASH_VALUES
        return if (remainder < 0) remainder + HASH_VALUES else remainder
    }
}
