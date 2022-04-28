package org.elder.sourcerer.eventstoredb.tests

import com.github.dockerjava.api.DockerClient
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

class EventstoreInstance : Closeable {
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

    var port: Int = 0
        private set

    fun ensureStarted() {
        if (container == null) {
            val createResponse = dockerClient.createContainerCmd(EVENTSTORE_IMAGE)
                    .withEnv(
                            "EVENTSTORE_CLUSTER_SIZE=1",
                            "EVENTSTORE_RUN_PROJECTIONS=All",
                            "EVENTSTORE_START_STANDARD_PROJECTIONS=true",
                            "EVENTSTORE_HTTP_PORT=2113",
                            "EVENTSTORE_INSECURE=true"
                    )
                    .withHostConfig(HostConfig
                            .newHostConfig()
                            .withPortBindings(PortBinding(
                                    Ports.Binding("localhost", null),
                                    ExposedPort.tcp(2113)
                            )))
                    .exec()
            dockerClient.startContainerCmd(createResponse.id).exec()
            val containerInfo = dockerClient.inspectContainerCmd(createResponse.id).exec()

            waitUntilHealth(createResponse.id)
            container = createResponse.id
            port = Integer.parseInt(containerInfo.networkSettings.ports.bindings[(ExposedPort(2113))]!![0].hostPortSpec)
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
                throw RuntimeException("EventStore failed to start up")
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