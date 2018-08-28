package org.elder.sourcerer.kotlin.utils

import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Convenience class for controlling concurrent flows in tests.
 */
class ConcurrencyProgress(private val name: String) {
    private val latch = CountDownLatch(1)

    fun await() {
        logger.debug("$name: awaiting")
        val happened = latch.await(2000, TimeUnit.MILLISECONDS)
        if (!happened) {
            throw IllegalStateException("$name did not happen in time")
        }
        logger.debug("$name: successfully awaited")
    }

    fun happened() {
        latch.countDown()
        logger.debug("$name: happened")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConcurrencyProgress::class.java)
    }
}
