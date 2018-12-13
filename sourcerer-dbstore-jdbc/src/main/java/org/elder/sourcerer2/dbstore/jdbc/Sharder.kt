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
                .asInt() % maxShards
    }

    private val shardHashFunction = Hashing.murmur3_128()
}
