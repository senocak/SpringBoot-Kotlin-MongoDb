package com.github.senocak.auth.config

import com.github.senocak.auth.util.logger
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager.RedisCacheManagerBuilder
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.time.Duration
import org.springframework.data.redis.core.RedisKeyValueAdapter
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories

@Configuration
@EnableCaching
@EnableRedisRepositories(enableKeyspaceEvents = RedisKeyValueAdapter.EnableKeyspaceEvents.ON_STARTUP)
class RedisConfig {
    private val log: Logger by logger()

    @Value("\${app.redis.HOST}") private var host: String? = null
    @Value("\${app.redis.PORT}") private var port: Int = 0
    @Value("\${app.redis.PASSWORD}") private var password: String? = null
    @Value("\${app.redis.TIMEOUT}") private var timeout: Int = 0

    @Bean
    fun jedisPool(): JedisPool {
        log.debug("RedisConfig: host=$host, port=$port, password=$password, timeout=$timeout")
        return JedisPool(JedisPoolConfig(), host, port, timeout, password)
    }

    /**
     * Create JedisPool
     * @return JedisPool
     */
    val jedisPool: JedisPool?
        get() = Companion.jedisPool

    @Bean
    fun redisConnectionFactory(): LettuceConnectionFactory {
        val redisStandaloneConfiguration = RedisStandaloneConfiguration()
        redisStandaloneConfiguration.hostName = host!!
        redisStandaloneConfiguration.setPassword(password)
        redisStandaloneConfiguration.port = port
        return LettuceConnectionFactory(redisStandaloneConfiguration)
    }

    @Bean
    fun cacheConfiguration(): RedisCacheConfiguration {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .disableCachingNullValues()
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    GenericJackson2JsonRedisSerializer()
                )
            )
    }

    @Bean
    fun redisCacheManagerBuilderCustomizer(): RedisCacheManagerBuilderCustomizer {
        return RedisCacheManagerBuilderCustomizer { builder: RedisCacheManagerBuilder ->
            val configurationMap: MutableMap<String, RedisCacheConfiguration> = HashMap()
            configurationMap["category"] =
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(30))
            builder.withInitialCacheConfigurations(configurationMap)
        }
    }

    companion object {
        private var jedisPool: JedisPool? = null
    }
}