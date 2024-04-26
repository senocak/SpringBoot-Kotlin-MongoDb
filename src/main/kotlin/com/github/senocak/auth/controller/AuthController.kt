package com.github.senocak.auth.controller

import com.github.senocak.auth.domain.JwtToken
import com.github.senocak.auth.domain.User
import com.github.senocak.auth.domain.UserEmailActivationSendEvent
import com.github.senocak.auth.domain.dto.ChangePasswordRequest
import com.github.senocak.auth.domain.dto.ExceptionDto
import com.github.senocak.auth.domain.dto.LoginRequest
import com.github.senocak.auth.domain.dto.RefreshTokenRequest
import com.github.senocak.auth.domain.dto.RegisterRequest
import com.github.senocak.auth.domain.dto.UserResponse
import com.github.senocak.auth.domain.dto.UserWrapperResponse
import com.github.senocak.auth.exception.ServerException
import com.github.senocak.auth.security.Authorize
import com.github.senocak.auth.security.JwtTokenProvider
import com.github.senocak.auth.service.MessageSourceService
import com.github.senocak.auth.service.UserService
import com.github.senocak.auth.util.AppConstants
import com.github.senocak.auth.util.OmaErrorMessageType
import com.github.senocak.auth.util.RoleName
import com.github.senocak.auth.util.convertEntityToDto
import com.github.senocak.auth.util.logger
import io.micrometer.core.annotation.Counted
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.parameters.RequestBody as RequestBodySchema
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.constraints.Email
import java.util.UUID
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.validation.BindingResult
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(BaseController.V1_AUTH_URL)
@Tag(name = "Authentication", description = "AUTH API")
class AuthController(
    private val userService: UserService,
    private val tokenProvider: JwtTokenProvider,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val eventPublisher: ApplicationEventPublisher,
    private val messageSourceService: MessageSourceService,
    @Value("\${app.jwtExpirationInMs}") private val jwtExpirationInMs: Long,
    @Value("\${app.refreshExpirationInMs}") private val refreshExpirationInMs: Long
): BaseController() {
    private val log: Logger by logger()

    @PostMapping("/login")
    @Operation(summary = "Login Endpoint", tags = ["Authentication"])
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "successful operation",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = UserWrapperResponse::class)))),
            ApiResponse(responseCode = "400", description = "Bad credentials",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class)))),
            ApiResponse(responseCode = "500", description = "internal server error occurred",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class))))
    ])
    @Counted
    @Throws(ServerException::class)
    fun login(
        @Parameter(description = "Request body to login", required = true) @Validated @RequestBody loginRequest: LoginRequest,
        resultOfValidation: BindingResult
    ): ResponseEntity<UserWrapperResponse> =
        validate(resultOfValidation = resultOfValidation)
            .run { authenticationManager.authenticate(UsernamePasswordAuthenticationToken(loginRequest.email, loginRequest.password)) }
            .run { userService.findByEmail(email = loginRequest.email) }
            .apply {
                if (this.emailActivatedAt == null)
                    messageSourceService.get(code = "email_not_activated")
                        .also { msg: String ->
                            log.error(msg)
                            throw ServerException(omaErrorMessageType = OmaErrorMessageType.UNAUTHORIZED,
                                statusCode = HttpStatus.UNAUTHORIZED, variables = arrayOf(msg)) }
            }
            .run {
                val generateUserWrapperResponse = generateUserWrapperResponse(user = this)
                val httpHeaders = userIdHeader(userId = "${this.id}")
                    .apply { this.add("jwtExpiresIn", "$jwtExpirationInMs") }
                    .apply { this.add("refreshExpiresIn", "$refreshExpirationInMs") }
                ResponseEntity.status(HttpStatus.OK).headers(httpHeaders).body(generateUserWrapperResponse)
            }

    @PostMapping("/register")
    @Operation(
        summary = "Register Endpoint",
        tags = ["Authentication"],
        responses = [
            ApiResponse(responseCode = "200", description = "successful operation",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = UserWrapperResponse::class)))),
            ApiResponse(responseCode = "400", description = "Bad credentials",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class)))),
            ApiResponse(responseCode = "500", description = "internal server error occurred",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class))))
    ])
    @ResponseStatus(code = HttpStatus.CREATED)
    @Throws(ServerException::class)
    fun register(
        @Parameter(description = "Request body to register", required = true) @Validated @RequestBody signUpRequest: RegisterRequest,
        resultOfValidation: BindingResult
    ): Map<String, String> {
        validate(resultOfValidation = resultOfValidation)
        if (userService.existsByEmail(email = signUpRequest.email))
            messageSourceService.get(code = "unique_email").plus(other = ": ${signUpRequest.email}")
                .apply { log.error(this) }
                .run { throw ServerException(omaErrorMessageType = OmaErrorMessageType.JSON_SCHEMA_VALIDATOR, variables = arrayOf(this)) }
        val user: User = User(name = signUpRequest.name, email = signUpRequest.email, password = passwordEncoder.encode(signUpRequest.password))
            .also { it.id = UUID.randomUUID() }
            .also { it.roles = listOf(element = RoleName.ROLE_USER.role) }
        val result: User = userService.save(user = user)
            .apply { eventPublisher.publishEvent(UserEmailActivationSendEvent(this, user)) }
            .also { log.info("UserRegisteredEvent is published for user: $user") }
        log.info("User created. User: $result")
        return mapOf("message" to messageSourceService.get(code = "email_has_to_be_verified"))
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh Token Endpoint",
        tags = ["Authentication"],
        responses = [
            ApiResponse(responseCode = "201", description = "successful operation",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = UserWrapperResponse::class)))),
            ApiResponse(responseCode = "400", description = "Bad credentials",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class)))),
            ApiResponse(responseCode = "500", description = "internal server error occurred",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class))))
    ])
    @Throws(ServerException::class)
    fun refresh(
        @Parameter(description = "Request body to refreshing token", required = true) @Validated @RequestBody refreshTokenRequest: RefreshTokenRequest,
        resultOfValidation: BindingResult
    ): UserWrapperResponse {
        validate(resultOfValidation = resultOfValidation)
        val userInfoCache: JwtToken = tokenProvider.findByTokenAndThrowException(token = refreshTokenRequest.token)
        if (userInfoCache.tokenType != "refresh")
            messageSourceService.get(code = "refresh_not_jwt")
                .apply { log.error(this) }
                .run {
                    throw ServerException(omaErrorMessageType = OmaErrorMessageType.BASIC_INVALID_INPUT,
                        variables = arrayOf(this), statusCode = HttpStatus.BAD_REQUEST)
                }
        val user: User = userService.findByEmail(email = userInfoCache.email)
           .also { tokenProvider.markLogoutEventForToken(email = it.email!!) }
        return generateUserWrapperResponse(user = user)
    }

    @PostMapping("/activate-email/{token}")
    @Operation(
        summary = "E-mail Activation Endpoint",
        tags = ["Authentication"],
        responses = [
            ApiResponse(responseCode = "200", description = "successful operation",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = Map::class)))),
            ApiResponse(responseCode = "401", description = "Bad credentials",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class))))
    ])
    fun activateEmail(
        @Parameter(description = "token variable", required = true, `in` = ParameterIn.PATH) @PathVariable token: String
    ): Map<String, String> =
        userService.activateEmail(token = token)
            .run { mapOf("message" to messageSourceService.get(code = "your_email_activated")) }

    @PostMapping("/resend-email-activation/{email}")
    @Operation(
        summary = "Resend E-mail Activation Endpoint",
        tags = ["Authentication"],
        responses = [
            ApiResponse(responseCode = "200", description = "successful operation",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = Map::class)))),
            ApiResponse(responseCode = "400", description = "Bad credentials",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class))))
    ])
    fun resendEmailActivation(
        @Parameter(description = "Email", required = true, `in` = ParameterIn.PATH) @PathVariable @Email(message = "{invalid_email}") email: String
    ): Map<String, String> =
        userService.findByEmail(email = email)
            .also { user: User ->
                if (user.emailActivatedAt != null) {
                    messageSourceService.get(code = "this_email_already_activated")
                        .also { msg: String -> log.error(msg) }
                        .run {
                            throw ServerException(omaErrorMessageType = OmaErrorMessageType.BASIC_INVALID_INPUT,
                                statusCode = HttpStatus.BAD_REQUEST, variables = arrayOf(this))
                        }
                }
            }
            .apply { eventPublisher.publishEvent(UserEmailActivationSendEvent(this, this)) }
            .also { user: User -> log.info("UserRegisteredEvent is re-published for user: $user") }
            .run { mapOf("message" to messageSourceService.get(code = "activation_email_sent")) }

    @PostMapping("/reset-password/{email}")
    @Operation(
        summary = "Reset Password Endpoint",
        responses = [
            ApiResponse(responseCode = "200", description = "Successful operation",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = Map::class)))),
            ApiResponse(responseCode = "400", description = "Bad credentials",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class))))
    ])
    fun resetPassword(
        @Parameter(description = "Email", required = true, `in` = ParameterIn.PATH) @PathVariable @Email(message = "{invalid_email}") email: String
    ): Map<String, String> =
        userService.passwordReset(email = email)
            .run { mapOf("message" to messageSourceService.get(code = "password_reset_link_sent")) }

    @PostMapping("/change-password/{token}")
    @Operation(
        summary = "Change Password Endpoint",
        responses = [
            ApiResponse(responseCode = "200", description = "Successful operation",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = Map::class)))),
            ApiResponse(responseCode = "400", description = "Bad credentials",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class))))
    ])
    fun changePassword(
        @RequestBodySchema(description = "Request body to change password", required = true, content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE)])
        @RequestBody @Validated request: ChangePasswordRequest,
        @Parameter(description = "Request body to change password", required = true, `in` = ParameterIn.PATH) @PathVariable token: String
    ): Map<String, String> =
        userService.changePassword(request = request, token = token)
            .run { mapOf("message" to messageSourceService.get(code = "password_changed_success")) }

    @PostMapping("/logout")
    @Authorize(roles = [AppConstants.ADMIN, AppConstants.USER])
    @Operation(summary = "Logout Endpoint", tags = ["Authentication"])
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "successful operation",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = UserWrapperResponse::class)))),
            ApiResponse(responseCode = "400", description = "Bad credentials",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class)))),
            ApiResponse(responseCode = "500", description = "internal server error occurred",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class))))
    ])
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    @Throws(ServerException::class)
    fun logout(request: HttpServletRequest) =
        userService.loggedInUser().also { tokenProvider.markLogoutEventForToken(email = it.email!!) }

    /**
     * Generate UserWrapperResponse with given UserResponse
     * @param user -- User entity that contains user data
     * @return UserWrapperResponse
     */
    private fun generateUserWrapperResponse(user: User): UserWrapperResponse {
        val userResponse: UserResponse = user.convertEntityToDto()
        val jwtToken: String = tokenProvider.generateJwtToken(email = user.email!!, roles = userResponse.roles)
        val refreshToken: String = tokenProvider.generateRefreshToken(email = user.email!!, roles = userResponse.roles)
        return UserWrapperResponse(userResponse = userResponse, token = jwtToken, refreshToken = refreshToken)
            .also { log.info("UserWrapperResponse is generated. UserWrapperResponse: $it") }
    }
}