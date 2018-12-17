package org.elder.sourcerer2.dbstore.jdbc

import com.google.common.hash.Hashing
import org.elder.sourcerer2.StreamId
import java.nio.charset.StandardCharsets

object Sharder {
    fun getShard(
            category: String,
            streamId: StreamId,
            maxShards: Int
    ): Int {
        return shardHashFunction.newHasher()
                .putString(category, StandardCharsets.UTF_8)
                .putString(streamId.identifier, StandardCharsets.UTF_8)
                .hash()
                .asInt()
                .getShard(maxShards)
    }

    private val shardHashFunction = Hashing.murmur3_128()
}

private fun Int.getShard(maxShards: Int): Int {
    val remainder = this % maxShards
    return if (remainder < 0) remainder + maxShards else remainder
}
