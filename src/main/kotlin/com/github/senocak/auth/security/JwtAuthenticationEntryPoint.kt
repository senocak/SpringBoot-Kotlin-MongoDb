package com.github.senocak.auth.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.senocak.auth.exception.RestExceptionHandler
import com.github.senocak.auth.util.logger
import org.slf4j.Logger
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import java.io.IOException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType

@Component
class JwtAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
    private val restExceptionHandler: RestExceptionHandler
) : AuthenticationEntryPoint {
    private val log: Logger by logger()

    @Throws(IOException::class)
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, ex: AuthenticationException) {
        log.error("Responding with unauthorized error. Message - ${ex.message}")
        val responseEntity: ResponseEntity<Any> = restExceptionHandler.handleUnAuthorized(ex = RuntimeException(ex.message))
        response.writer.write(objectMapper.writeValueAsString(responseEntity.body))
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
    }
}