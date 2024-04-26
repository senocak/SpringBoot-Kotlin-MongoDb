package com.github.senocak.auth.config.initializer

import com.github.senocak.auth.TestConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import redis.clients.jedis.Jedis

@TestConfiguration
class RedisInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    private lateinit var jedis: Jedis

    override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
        val host: String = CONTAINER.host
        val port: Int = CONTAINER.firstMappedPort
        TestPropertyValues.of(
            "REDIS_HOST=$host",
            "REDIS_PORT=$port",
            "REDIS_PASSWORD=" + "",
        ).applyTo(configurableApplicationContext.environment)

        jedis = Jedis(host, port)
        assertEquals("PONG", jedis.ping())
        setInitialValuesRedis()
    }

    private fun setInitialValuesRedis() {
        jedis.configSet("notify-keyspace-events", "KEA")
        val emailConfig: MutableMap<String, String> = HashMap()
        emailConfig["protocol"] = "protocol"
        emailConfig["host"] = "host"
        emailConfig["port"] = "port"
        emailConfig["from"] = "from"
        emailConfig["password"] = "password"
        jedis.hmset("email", emailConfig)
    }

    companion object {

        @Container private var CONTAINER: GenericContainer<*> = GenericContainer("redis:6.2-alpine")
            .withExposedPorts(6379)
            .withStartupTimeout(TestConstants.CONTAINER_WAIT_TIMEOUT)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
            .withReuse(true)

        init {
            CONTAINER.start()
        }
    }
}
