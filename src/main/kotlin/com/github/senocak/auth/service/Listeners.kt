package com.github.senocak.auth.service

import com.github.senocak.auth.config.DataSourceConfig
import com.github.senocak.auth.domain.JwtToken
import com.github.senocak.auth.domain.PasswordResetToken
import com.github.senocak.auth.domain.User
import com.github.senocak.auth.domain.UserEmailActivationSendEvent
import com.github.senocak.auth.util.RoleName
import com.github.senocak.auth.util.logger
import com.github.senocak.auth.util.randomStringGenerator
import java.util.Date
import java.util.UUID
import org.slf4j.Logger
import org.slf4j.MDC
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.redis.core.RedisKeyExpiredEvent
import org.springframework.data.repository.init.RepositoriesPopulatedEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.support.RequestHandledEvent

@Component
@Async
class Listeners(
    private val emailService: EmailService,
    private val dataSourceConfig: DataSourceConfig,
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder
){
    private val log: Logger by logger()

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReadyEvent(event: ApplicationReadyEvent) {
        if (dataSourceConfig.ddl == "create") {
            User(name = "anil1", email = "anil1@senocak.com", password = passwordEncoder.encode("asenocak"))
                .also {
                    it.id = UUID.fromString("2cb9374e-4e52-4142-a1af-16144ef4a27d")
                    it.roles = listOf(RoleName.ROLE_USER.role, RoleName.ROLE_ADMIN.role)
                    it.emailActivatedAt = Date()
                }
                .run {
                    userService.save(user = this)
                }

            User(name = "anil2", email = "anil2@gmail.com", password = passwordEncoder.encode("asenocak"))
                .also {
                    it.id = UUID.fromString("3cb9374e-4e52-4142-a1af-16144ef4a27d")
                    it.roles = listOf(RoleName.ROLE_USER.role)
                }
                .run {
                    userService.save(user = this)
                }
        }
    }

    @Transactional
    @EventListener(UserEmailActivationSendEvent::class)
    fun onUserRegisteredEvent(event: UserEmailActivationSendEvent): Unit =
        event.user
            .also { MDC.put("userId", "${event.user.id}") }
            .also { log.info("[UserRegisteredEvent] ${it.email} - ${it.id}") }
            .run {
                event.user.emailActivationToken = 15.randomStringGenerator()
                userService.save(user = event.user)
            }
            .run {
                emailService.sendUserEmailActivation(user = this, emailActivationToken = this.emailActivationToken!!)
            }
            .also { MDC.remove("userId") }

    @EventListener(RedisKeyExpiredEvent::class)
    fun onRedisKeyExpiredEvent(event: RedisKeyExpiredEvent<Any?>): Unit =
        log.info("[RedisKeyExpiredEvent] $event")
            .run { event.value }
            .run {
                when {
                    this == null -> {
                        log.warn("Value is null, returning...")
                        return
                    }
                    else -> this
                }
            }
            .run {
                when (this.javaClass) {
                    JwtToken::class.java -> {
                        val jwtToken: JwtToken = this as JwtToken
                        log.info("Expired JwtToken: $jwtToken")
                    }
                    PasswordResetToken::class.java -> {
                        val passwordResetToken: PasswordResetToken = this as PasswordResetToken
                        MDC.put("userId", "${passwordResetToken.userId}")
                        log.info("Expired PasswordResetToken: $passwordResetToken")
                        MDC.remove("userId")
                    }
                    else -> log.warn("Unhandled event: ${this.javaClass} for redis...")
                }
            }

    @EventListener(RequestHandledEvent::class)
    fun onRequestHandledEvent(event: RequestHandledEvent): Unit = log.info("[RequestHandledEvent]: $event")

    @EventListener(RepositoriesPopulatedEvent::class)
    fun onRepositoriesPopulatedEvent(event: RepositoriesPopulatedEvent): Unit = log.info("[RepositoriesPopulatedEvent]: $event")
}
