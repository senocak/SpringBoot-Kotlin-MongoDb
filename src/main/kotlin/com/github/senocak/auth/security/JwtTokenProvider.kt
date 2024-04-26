package com.github.senocak.auth.security

import com.github.senocak.auth.domain.JwtToken
import com.github.senocak.auth.domain.JwtTokenRepository
import com.github.senocak.auth.exception.ServerException
import com.github.senocak.auth.service.MessageSourceService
import com.github.senocak.auth.util.OmaErrorMessageType
import com.github.senocak.auth.util.logger
import com.github.senocak.auth.util.randomStringGenerator
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jws
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import java.security.Key
import java.util.Date
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component

@Component
class JwtTokenProvider(
    private val jwtTokenRepository: JwtTokenRepository,
    private val messageSourceService: MessageSourceService
) {
    private val log: Logger by logger()

    @Value("\${app.jwtSecret}") private lateinit var jwtSecret: String
    @Value("\${app.jwtExpirationInMs}") private lateinit var jwtExpirationInMs: String
    @Value("\${app.refreshExpirationInMs}") private lateinit var refreshExpirationInMs: String

    /**
     * Generating the jwt token
     * @param email -- email
     */
    fun generateJwtToken(email: String, roles: List<String?>): String =
        generateToken(subject = email, roles = roles, expirationInMs = jwtExpirationInMs.toLong())
            .apply {
                jwtTokenRepository.save(JwtToken(token = this, email = email, timeToLive = jwtExpirationInMs.toLong(), tokenType = "jwt"))
            }

    /**
     * Generating the refresh token by random string
     * @param email -- email
     */
    fun generateRefreshToken(email: String, roles: List<String?>): String =
        50.randomStringGenerator()
            .apply {
                jwtTokenRepository.save(JwtToken(token = this, email = email, timeToLive = refreshExpirationInMs.toLong(), tokenType = "refresh"))
            }

    /**
     * Generating the token
     * @param subject -- userId
     */
    private fun generateToken(subject: String, roles: List<String?>, expirationInMs: Long): String =
        HashMap<String, Any>()
            .also { it["roles"] = roles }
            .run {
                val now = Date()
                Jwts.builder()
                    .setClaims(this)
                    .setSubject(subject)
                    .setIssuedAt(now)
                    .setExpiration(Date(now.time + expirationInMs))
                    .signWith(signKey, SignatureAlgorithm.HS256)
                    .compact()
            }

    private val signKey: Key
        get() = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))

    /**
     * Get the jws claims
     * @param token -- jwt token
     * @return -- expiration date
     */
    @Throws(
        ExpiredJwtException::class,
        UnsupportedJwtException::class,
        MalformedJwtException::class,
        SignatureException::class,
        IllegalArgumentException::class
    )
    private fun getJwsClaims(token: String): Jws<Claims?> = Jwts.parserBuilder().setSigningKey(signKey).build().parseClaimsJws(token)

    /**
     * @param token -- jwt token
     * @return -- userName from jwt
     */
    fun getUserEmailFromJWT(token: String): String = getJwsClaims(token).body!!.subject.run { this }

    /**
     * @param token -- jwt token
     */
    fun validateToken(token: String): JwtToken {
        try {
            findByToken(token = token)
                ?: "Token is not found in redis."
                    .apply { log.error(this) }
                    .run { throw AccessDeniedException(this) }
            val email: String = getUserEmailFromJWT(token = token)
            return jwtTokenRepository.findByEmail(email = email)
                ?: "Token by email: $email is not found in redis."
                    .apply { log.error(this) }
                    .run { throw AccessDeniedException(this) }
        } catch (ex: SignatureException) {
            "Invalid JWT signature"
                .apply { log.error(this) }
                .run { throw AccessDeniedException(this) }
        } catch (ex: MalformedJwtException) {
            "Invalid JWT token"
                .apply { log.error(this) }
                .run { throw AccessDeniedException(this) }
        } catch (ex: ExpiredJwtException) {
            "Expired JWT token"
                .apply { log.error(this) }
                .run { throw AccessDeniedException(this) }
        } catch (ex: UnsupportedJwtException) {
            "Unsupported JWT token"
                .apply { log.error(this) }
                .run { throw AccessDeniedException(this) }
        } catch (ex: IllegalArgumentException) {
            "JWT claims string is empty."
                .apply { log.error(this) }
                .run { throw AccessDeniedException(this) }
        } catch (ex: AccessDeniedException) {
            ex.message
                .apply { log.error(this) }
                .run { throw AccessDeniedException(this) }
        } catch (ex: Exception) {
            "Undefined exception occurred: ${ex.message}"
                .apply { log.error(this) }
                .run { throw AccessDeniedException(this) }
        }
    }

    fun findByTokenAndThrowException(token: String): JwtToken =
        findByToken(token = token)
            ?: throw ServerException(omaErrorMessageType = OmaErrorMessageType.NOT_FOUND, statusCode = HttpStatus.NOT_FOUND,
                variables = arrayOf(messageSourceService.get(code = "token_not_found_in_redis")))

    fun findByToken(token: String): JwtToken? = jwtTokenRepository.findByToken(token = token)

    /**
     * When user logging out or create a new token with refresh token, all the tokens should be removed from cache for security
     * @param email String user email which is added to the key as value
     */
    fun markLogoutEventForToken(email: String) {
        log.info("Logged out. Jwt and Refresh tokens for user $email removed in redis")
        val allByUserId: List<JwtToken?> = jwtTokenRepository.findAllByEmail(email = email)
        for (jwtToken: JwtToken? in allByUserId) {
            log.info("[JwtToken]: UserId: ${jwtToken?.email}, Token: ${jwtToken?.token}, TimeToLive: ${jwtToken?.timeToLive}")
        }
        jwtTokenRepository.deleteAll(allByUserId)
    }
}