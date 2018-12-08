package org.elder.sourcerer2.kotlin.utils

import org.junit.rules.ExternalResource
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * JUnit rule for running blocks of code in a separate thread.
 *
 * At the end of the test, it waits for all of them to complete, throwing any exception that
 * occurred.
 */
class ConcurrencyRule: ExternalResource(){
    private val jobs = mutableListOf<Job>()

    fun runInThread(name: String, action: () -> Unit) {
        jobs += Job(name, action)
    }

    override fun after() {
        jobs.forEach { it.awaitCompletion() }
    }

    private class Job(
            name: String,
            private val action: () -> Unit
    ) {
        private val exception = AtomicReference<Throwable>()
        private val runningThread = thread(start = false, name = name) {
            action()
        }.apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e -> exception.set(e) }
            start()
        }

        fun awaitCompletion() {
            logger.debug("${runningThread.name}: Awaiting completion")
            runningThread.join()
            exception.get()?.let { throw it }
            logger.debug("${runningThread.name}: successfully completed")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ConcurrencyRule::class.java)
    }
}
