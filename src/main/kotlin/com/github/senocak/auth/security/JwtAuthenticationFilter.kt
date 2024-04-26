package com.github.senocak.auth.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.senocak.auth.exception.RestExceptionHandler
import com.github.senocak.auth.service.UserService
import com.github.senocak.auth.util.AppConstants.TOKEN_HEADER_NAME
import com.github.senocak.auth.util.AppConstants.TOKEN_PREFIX
import com.github.senocak.auth.util.logger
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
import org.apache.commons.lang3.exception.ExceptionUtils
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.SmartLifecycle
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * Filter class that aims to guarantee a single execution per request dispatch, on any servlet container.
 * @return -- an JwtAuthenticationFilter instance
 */
@Component
class JwtAuthenticationFilter(
    private val tokenProvider: JwtTokenProvider,
    private val userService: UserService,
    private val objectMapper: ObjectMapper,
    private val authenticationManager: AuthenticationManager,
    private val restExceptionHandler: RestExceptionHandler,
    @Qualifier("requestMappingHandlerMapping")
    private val requestHandlerMapping: RequestMappingHandlerMapping
): OncePerRequestFilter(), SmartLifecycle {
    private val log: Logger by logger()
    private var running: Boolean = false
    private val protectedEndpoints: HashMap<String, MutableList<String>> =
        hashMapOf<String, MutableList<String>>()
            .also { it["GET"] = arrayListOf() }
            .also { it["POST"] = arrayListOf() }
            .also { it["PATCH"] = arrayListOf() }
            .also { it["DELETE"] = arrayListOf() }

    /**
     * Guaranteed to be just invoked once per request within a single request thread.
     * @param request -- Request information for HTTP servlets.
     * @param response -- It is where the servlet can write information about the data it will send back.
     * @param filterChain -- An object provided by the servlet container to the developer giving a view into the invocation chain of a filtered request for a resource.
     * @throws ServletException -- Throws ServletException
     * @throws IOException -- Throws IOException
     */
    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        try {
            val isProtectedApiBeingInvoked: Boolean = isProtectedRequest(httpMethod = request.method, request = request)
            if (isProtectedApiBeingInvoked) {
                val bearerToken: String = request.getHeader(TOKEN_HEADER_NAME)
                    ?: "Bearer Token should be provided in $TOKEN_HEADER_NAME header"
                        .apply { log.error(this) }
                        .apply { throw AccessDeniedException(this) }
                if (!bearerToken.startsWith(prefix = TOKEN_PREFIX)) {
                    "Token should start with $TOKEN_PREFIX"
                        .apply { log.error(this) }
                        .apply { throw AccessDeniedException(this) }
                }
                val jwt: String = bearerToken.substring(startIndex = 7)
                tokenProvider.validateToken(token = jwt)
                val email: String = tokenProvider.getUserEmailFromJWT(token = jwt)
                val userDetails: UserDetails = userService.loadUserByUsername(email = email)
                UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.authorities)
                    .also { it.details = WebAuthenticationDetailsSource().buildDetails(request) }
                    .also { authenticationManager.authenticate(it) }
                    .also { log.trace("SecurityContext created") }
            }
        } catch (ex: Exception) {
            val responseEntity: ResponseEntity<Any> = restExceptionHandler.handleUnAuthorized(ex = RuntimeException(ex.message))
            response.writer.write(objectMapper.writeValueAsString(responseEntity.body))
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            log.error("Could not set user authentication in security context. Error: {}", ExceptionUtils.getMessage(ex))
            return
        }
        response.setHeader("Access-Control-Allow-Origin", "*")
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT")
        response.setHeader("Access-Control-Allow-Headers",
            "Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With")
        response.setHeader("Access-Control-Expose-Headers",
            "Content-Type, Access-Control-Expose-Headers, Authorization, X-Requested-With")
        filterChain.doFilter(request, response)
        log.trace("Filtering accessed for remote address: ${request.remoteAddr}")
    }

    override fun start(): Unit = init().also { running = true }
    override fun stop(): Unit = super.destroy().run { running = false }
    override fun isRunning(): Boolean = running

    /**
     * Initializes the class by gathering public endpoints for various HTTP methods.
     * It identifies designated public endpoints within the application's mappings
     * and adds them to separate lists based on their associated HTTP methods.
     * If OpenAPI is enabled, Swagger endpoints are also considered as public.
     */
    fun init() {
        requestHandlerMapping
            .handlerMethods
            .forEach { (requestInfo: RequestMappingInfo, handlerMethod: HandlerMethod) ->
                val methods: MutableSet<RequestMethod> = requestInfo.methodsCondition.methods
                if (methods.isNotEmpty()) {
                    for (method: RequestMethod in methods)
                        if (
                            handlerMethod.method.declaringClass.isAnnotationPresent(Authorize::class.java) ||
                            handlerMethod.hasMethodAnnotation(Authorize::class.java)
                        )
                            protectedEndpoints[method.asHttpMethod().name()]?.addAll(elements = requestInfo.pathPatternsCondition!!.patternValues)
                }
            }
    }

    /**
     * Checks if the provided HTTP request is directed towards an unsecured API endpoint.
     *
     * @param request The HTTP request to inspect.
     * @return `true` if the request is to an unsecured API endpoint, `false` otherwise.
     */
    fun isProtectedRequest(httpMethod: String, request: HttpServletRequest): Boolean =
        protectedEndpoints[httpMethod]
            ?.any { apiPath: String -> AntPathMatcher().match(apiPath, request.requestURI) }
            ?: false
}