package com.github.senocak.auth.config.initializer

import com.github.senocak.auth.TestConstants
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container

@TestConfiguration
class MongoInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
        TestPropertyValues.of(
            "spring.datasource.url=" + CONTAINER.connectionString,
            "spring.datasource.database=boilerplatetest",
            "spring.datasource.ddl=create"
        ).applyTo(configurableApplicationContext.environment)
    }

    companion object {
        @Container private var CONTAINER = MongoDBContainer("mongo:6.0.3")
            .withExposedPorts(27017)
            .withEnv("MONGO_INITDB_DATABASE", "local")
            .withStartupTimeout(TestConstants.CONTAINER_WAIT_TIMEOUT)

        init {
            CONTAINER.start()
        }
    }
}