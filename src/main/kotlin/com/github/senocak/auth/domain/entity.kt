package com.github.senocak.auth.domain

import com.github.senocak.auth.util.RoleName
import java.io.Serializable
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.TimeToLive
import org.springframework.data.redis.core.index.Indexed

open class BaseDomain(
    @Id var id: UUID? = null,
    @Field @CreatedDate var createdAt: Date = Date(),
    @Field @LastModifiedDate var updatedAt: Date = Date()
): Serializable

@Document(collection = "users")
data class User(
    @Field var name: String? = null,
    @Field var email: String? = null,
    @Field var password: String? = null
): BaseDomain() {
    @Field
    var roles: List<String> = arrayListOf()

    @Field var emailActivationToken: String? = null
    @Field var emailActivatedAt: Date? = null
}

@RedisHash(value = "jwtTokens")
data class JwtToken (
    @Id
    @Indexed
    val token: String,

    val tokenType: String,

    @Indexed
    val email: String,

    @TimeToLive(unit = TimeUnit.MILLISECONDS)
    val timeToLive: Long = TimeUnit.MINUTES.toMillis(30)
)

@RedisHash("password_reset_tokens")
data class PasswordResetToken(
    @Id
    @Indexed
    val token: String,

    @Indexed
    val userId: UUID,

    @TimeToLive(unit = TimeUnit.MILLISECONDS)
    val timeToLive: Long = TimeUnit.MINUTES.toMillis(30)
)