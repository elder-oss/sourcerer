package org.elder.sourcerer.eventstore.tests

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.AsyncDockerCmd
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import java.io.Closeable
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class EventstoreInstance(val enableLegacyTcpInterface: Boolean = false) : Closeable {
    private val dockerClient: DockerClient
    private var container: String? = null

    init {
        // Use defaults / settings from environment variables
        val dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
        val dockerHttpClient = ApacheDockerHttpClient.Builder()
                .dockerHost(dockerConfig.dockerHost)
                .sslConfig(dockerConfig.sslConfig)
                .build()
        this.dockerClient = DockerClientImpl.getInstance(dockerConfig, dockerHttpClient)
    }

    var tcpPort: Int = 0
        private set

    var httpPort: Int = 0
        private set

    fun ensureStarted() {
        if (container == null) {
            runAsyncCommandBlocking(dockerClient.pullImageCmd(EVENTSTORE_IMAGE))
            val env = listOf(
                    "EVENTSTORE_CLUSTER_SIZE=1",
                    "EVENTSTORE_RUN_PROJECTIONS=All",
                    "EVENTSTORE_START_STANDARD_PROJECTIONS=true",
                    "EVENTSTORE_HTTP_PORT=2113",
                    "EVENTSTORE_INSECURE=true") +
                    if (enableLegacyTcpInterface) listOf(
                            "EVENTSTORE_ENABLE_EXTERNAL_TCP=true",
                            "EVENTSTORE_EXT_TCP_PORT=1113"
                    )
                    else listOf()

            val createResponse = dockerClient.createContainerCmd(EVENTSTORE_IMAGE)
                    .withEnv(*env.toTypedArray())
                    .withHostConfig(HostConfig
                            .newHostConfig()
                            .withPortBindings(
                                    PortBinding(
                                            Ports.Binding("localhost", null),
                                            ExposedPort.tcp(1113)
                                    ),
                                    PortBinding(
                                            Ports.Binding("localhost", null),
                                            ExposedPort.tcp(2113)
                                    )))
                    .exec()
            dockerClient.startContainerCmd(createResponse.id).exec()
            val containerInfo = dockerClient.inspectContainerCmd(createResponse.id).exec()

            waitUntilHealth(createResponse.id)
            container = createResponse.id
            tcpPort = Integer.parseInt(containerInfo.networkSettings.ports.bindings[(ExposedPort(1113))]!![0].hostPortSpec)
            httpPort = Integer.parseInt(containerInfo.networkSettings.ports.bindings[(ExposedPort(2113))]!![0].hostPortSpec)
        }
    }

    private fun <T, C : AsyncDockerCmd<C, T>> runAsyncCommandBlocking(cmd: AsyncDockerCmd<C, T>) {
        val resultCallback = ResultCallback.Adapter<T>()
        cmd.exec(resultCallback)
        if (!resultCallback.awaitCompletion(60, TimeUnit.SECONDS)) {
            throw RuntimeException("Async docker command did not complete within timeout")
        }
    }

    private fun waitUntilHealth(containerId: String) {
        val timeAtStart = Instant.now()
        while (true) {
            val containerInfo = dockerClient.inspectContainerCmd(containerId).exec()
            when (containerInfo.state.health.status) {
                "healthy" -> return
                "unhealthy" -> throw RuntimeException("Eventstore failed to start")
            }

            Thread.sleep(1000)
            if (Duration.between(timeAtStart, Instant.now()).seconds > STARTUP_TIMEOUT_SECONDS) {
                throw RuntimeException("EventStore failed to start up in time")
            }
        }
    }

    override fun close() {
        container?.let {
            dockerClient.killContainerCmd(it).exec()
            dockerClient.removeContainerCmd(it).exec()
            container = null
        }
    }

    companion object {
        const val EVENTSTORE_IMAGE = "eventstore/eventstore:21.10.2-buster-slim"
        const val STARTUP_TIMEOUT_SECONDS = 60
    }
}