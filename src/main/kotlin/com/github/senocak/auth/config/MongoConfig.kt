package com.github.senocak.auth.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import java.util.Optional
import org.bson.UuidRepresentation
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor
import org.springframework.data.domain.AuditorAware
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration
import org.springframework.data.mongodb.config.EnableMongoAuditing
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component

@Configuration
@EnableMongoAuditing
class MongoConfig(
    private val dataSourceConfig: DataSourceConfig
): AbstractMongoClientConfiguration() {
    override fun getDatabaseName(): String = dataSourceConfig.database

    override fun mongoClient(): MongoClient =
        MongoClients.create(
            MongoClientSettings.builder()
                .applyConnectionString(ConnectionString("${dataSourceConfig.url}/${dataSourceConfig.database}?authSource=admin"))
                .uuidRepresentation(UuidRepresentation.STANDARD)
            .build()
        )

    public override fun getMappingBasePackages(): MutableCollection<String> =  arrayListOf("com.github.senocak.auth.")

    @Bean
    fun auditorAwareRef(): AuditorAware<String> = AuditorAware<String> { Optional.of("Mr. Senocak") }

    @Bean
    fun mongoTemplate(): MongoTemplate = MongoTemplate(mongoClient(), databaseName)

    @Bean
    fun exceptionTranslation(): PersistenceExceptionTranslationPostProcessor = PersistenceExceptionTranslationPostProcessor()
}

@Component
@ConfigurationProperties(prefix = "spring.datasource")
class DataSourceConfig {
    lateinit var url: String
    lateinit var database: String
    lateinit var ddl: String
}
