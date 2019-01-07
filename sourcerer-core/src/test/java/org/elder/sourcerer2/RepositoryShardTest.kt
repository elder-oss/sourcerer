package org.elder.sourcerer2

import org.junit.Assert
import org.junit.Test

class RepositoryShardTest {
    @Test
    fun testHappyPath1() {
        val shard = RepositoryShard(4, 16)
        Assert.assertEquals(4, shard.shardIndex)
        Assert.assertEquals(16, shard.shardCount)
        Assert.assertEquals(4, shard.exponent)
    }
}