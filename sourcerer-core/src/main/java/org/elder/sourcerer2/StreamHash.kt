package org.elder.sourcerer2

import com.google.common.hash.Hashing
import java.nio.charset.StandardCharsets

/**
 * Represents a unique integer value deterministically derived from an individual stream identifier, used for splitting
 * the streams in a repository into logical shards.
 */
data class StreamHash(
        val value: Int
) {
    init {
        if (value < 0) {
            throw IllegalArgumentException("Stream hash must be a positive number")
        }
        if (value > MAX_HASH_VALUE) {
            throw IllegalArgumentException("Stream hash cannot be greater than $MAX_HASH_VALUE")
        }
    }

    fun toShard(shardCount: Int): RepositoryShard {
        val shardIndex = (value.toLong() * shardCount / MAX_HASH_VALUE).toInt()
        return RepositoryShard(shardIndex, shardCount)
    }

    companion object {
        // Select an arbitrary but large number of unique shard values to use, this is
        // ~10^9 and can safely be represented as a signed 32 bit integer value
        const val HASH_VALUES = 0x40000000
        private const val MAX_HASH_VALUE = HASH_VALUES - 1
        private val shardHashFunction = Hashing.murmur3_128()


        private fun Int.getHash(): StreamHash {
            val remainder = this % StreamHash.HASH_VALUES
            val intHash = if (remainder < 0) remainder + StreamHash.HASH_VALUES else remainder
            return StreamHash(intHash)
        }

        @JvmStatic
        fun fromStreamId(streamId: StreamId): StreamHash {
            return StreamHash.shardHashFunction.newHasher()
                    .putString(streamId.identifier, StandardCharsets.UTF_8)
                    .hash()
                    .asInt()
                    .getHash()
        }
    }
}

fun StreamId.toStreamHash(): StreamHash {
    return StreamHash.fromStreamId(this)
}
