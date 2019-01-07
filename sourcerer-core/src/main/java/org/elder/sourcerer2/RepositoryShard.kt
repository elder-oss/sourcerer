package org.elder.sourcerer2

/**
 * Describes a logical shard from an event repository. The shard can be used to segment batch or online processing of
 * of events from a repository. Shards have the property that - regardless of the shard count - no two shard with the
 * same count but different index can contain the same event stream, and that each event stream will belong to at least
 * one shard.
 */
data class RepositoryShard(
        /**
         * The index of this shard. The index is 0 based and must be less than the shard count.
         */
        val shardIndex: Int,

        /**
         * The number of logical shards to break the repository in to. This must be a power of two, and in the range
         * from 1 to 256 inclusive.
         */
        val shardCount: Int
) {
    init {
        if (shardCount < 1) {
            throw IllegalArgumentException("Shard count must be >= 1")
        }
        if (shardCount > MAX_SHARD_COUNT) {
            throw IllegalArgumentException("Shard count cannot be greater than ${RepositoryShard.MAX_SHARD_COUNT}")
        }
        if (shardCount and (shardCount - 1) != 0) {
            // Possibly too clever, see https://graphics.stanford.edu/~seander/bithacks.html#DetermineIfPowerOf2
            throw IllegalArgumentException("Shard count must be a power of two")
        }
        if (shardIndex < 0) {
            throw IllegalArgumentException("Shard index must be positive")
        }
        if (shardIndex >= shardCount) {
            throw IllegalArgumentException("Shard index must be less than the shard count ($shardCount)")
        }
    }

    /**
     * Gets the shard count base 2 exponent, e.g. 0 for a shard count of 1, 8 for a shard count of 256.
     */
    val exponent = log2(shardCount)

    companion object {
        const val MAX_SHARD_COUNT = 256

        private fun log2(value: Int): Int {
            var tmp = value
            var res = 0

            while (true) {
                tmp = tmp shr 1
                if (tmp == 0) {
                    break
                }
                res++
            }

            return res
        }
    }
}
