package com.github.senocak.auth.controller

import com.github.senocak.auth.TestConstants.USER_EMAIL
import com.github.senocak.auth.TestConstants.USER_NAME
import com.github.senocak.auth.TestConstants.USER_PASSWORD
import com.github.senocak.auth.createTestUser
import com.github.senocak.auth.domain.JwtToken
import com.github.senocak.auth.domain.User
import com.github.senocak.auth.domain.UserEmailActivationSendEvent
import com.github.senocak.auth.domain.dto.ChangePasswordRequest
import com.github.senocak.auth.domain.dto.LoginRequest
import com.github.senocak.auth.domain.dto.RefreshTokenRequest
import com.github.senocak.auth.domain.dto.RegisterRequest
import com.github.senocak.auth.domain.dto.UserWrapperResponse
import com.github.senocak.auth.exception.ServerException
import com.github.senocak.auth.security.JwtTokenProvider
import com.github.senocak.auth.service.MessageSourceService
import com.github.senocak.auth.service.UserService
import com.github.senocak.auth.util.OmaErrorMessageType
import jakarta.servlet.http.HttpServletRequest
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.function.Executable
import org.mockito.ArgumentMatchers.anyList
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.validation.BindingResult

@Tag("unit")
@ExtendWith(MockitoExtension::class)
@DisplayName("Unit Tests for AuthController")
class AuthControllerTest {
    lateinit var authController: AuthController
    private val userService: UserService = mock<UserService>()
    private val tokenProvider: JwtTokenProvider = mock<JwtTokenProvider>()
    private val passwordEncoder: PasswordEncoder = mock<PasswordEncoder>()
    private val authenticationManager: AuthenticationManager = mock<AuthenticationManager>()
    private val eventPublisher: ApplicationEventPublisher = mock<ApplicationEventPublisher>()
    private val messageSourceService: MessageSourceService = mock<MessageSourceService>()
    private val authentication: Authentication = mock<Authentication>()
    private val bindingResult: BindingResult = mock<BindingResult>()
    private var user: User = createTestUser()

    @BeforeEach
    fun init() {
        authController = AuthController(
            userService = userService,
            tokenProvider = tokenProvider,
            passwordEncoder = passwordEncoder,
            authenticationManager = authenticationManager,
            eventPublisher = eventPublisher,
            messageSourceService = messageSourceService,
            jwtExpirationInMs = 100,
            refreshExpirationInMs = 100
        )
    }

    @Nested
    internal inner class LoginTest {
        private val loginRequest: LoginRequest = LoginRequest(email = USER_EMAIL, password = USER_PASSWORD)
        @BeforeEach
        fun setup() {
            loginRequest.email = USER_NAME
            loginRequest.password = USER_PASSWORD
        }

        @Test
        @Throws(ServerException::class)
        fun givenSuccessfulPath_whenLogin_thenReturn200() {
            // Given
            whenever(methodCall = authenticationManager.authenticate(UsernamePasswordAuthenticationToken(loginRequest.email, loginRequest.password)))
                .thenReturn(authentication)
            whenever(methodCall = userService.findByEmail(email = loginRequest.email)).thenReturn(user)
            val generatedToken = "generatedToken"
            whenever(methodCall = tokenProvider.generateJwtToken(email = eq(user.email!!), roles = anyList())).thenReturn(generatedToken)
            whenever(methodCall = tokenProvider.generateRefreshToken(email = eq(user.email!!), roles = anyList())).thenReturn(generatedToken)
            // When
            val response: ResponseEntity<UserWrapperResponse> = authController.login(loginRequest = loginRequest, resultOfValidation = bindingResult)
            // Then
            assertNotNull(response)
            assertNotNull(response.body)
            assertEquals(generatedToken, response.body!!.token)
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(user.name, response.body!!.userResponse.name)
            assertEquals(user.email, response.body!!.userResponse.email)
            assertEquals(user.roles.size, response.body!!.userResponse.roles.size)
            assertEquals("generatedToken", response.body!!.token)
            assertEquals("generatedToken", response.body!!.refreshToken)
        }
    }

    @Nested
    internal inner class RegisterTest {
        private val registerRequest: RegisterRequest = RegisterRequest(
            name = USER_NAME,
            email = USER_EMAIL,
            password = USER_PASSWORD
        )

        @Test
        fun givenExistMail_whenRegister_thenThrowServerException() {
            // Given
            whenever(methodCall = userService.existsByEmail(email = registerRequest.email)).thenReturn(true)
            // When
            val closureToTest = Executable { authController.register(signUpRequest = registerRequest, resultOfValidation = bindingResult) }
            // Then
            assertThrows(ServerException::class.java, closureToTest)
        }

        @Test
        fun given_whenRegister_thenAssertResult() {
            // Given
            doReturn(value = "pass1").`when`(passwordEncoder).encode(registerRequest.password)
            doReturn(value = user).`when`(userService).save(user = any())
            doReturn(value = "email_has_to_be_verified").`when`(messageSourceService).get(code = "email_has_to_be_verified")
            // When
            val response: Map<String, String> = authController.register(signUpRequest = registerRequest, resultOfValidation = bindingResult)
            // Then
            assertNotNull(response)
            assertNotNull(response["message"])
            assertEquals("email_has_to_be_verified", response["message"])
        }
    }

    @Nested
    internal inner class RefreshTest {
        private val refreshTokenRequest: RefreshTokenRequest = RefreshTokenRequest(token = "token")

        @Test
        @Throws(ServerException::class)
        fun given_whenRefreshWithNotValidTokenType_thenThrowServerException() {
            // Given
            val jwtToken = JwtToken(token = "token", tokenType = "not_refresh", email = USER_EMAIL, timeToLive = 10)
            whenever(methodCall = tokenProvider.findByTokenAndThrowException(token = "token")).thenReturn(jwtToken)
            whenever(methodCall = messageSourceService.get(code = "refresh_not_jwt")).thenReturn("refresh_not_jwt")

            val generatedToken = "generatedToken"
            whenever(methodCall = tokenProvider.generateJwtToken(email = eq(user.email!!), roles = anyList())).thenReturn(generatedToken)
            // When
            val closureToTest = Executable { authController.refresh(refreshTokenRequest = refreshTokenRequest, resultOfValidation = bindingResult) }
            // Then
            val assertThrows: ServerException = assertThrows(ServerException::class.java, closureToTest)
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows.statusCode)
            assertEquals(OmaErrorMessageType.BASIC_INVALID_INPUT, assertThrows.omaErrorMessageType)
        }

        @Test
        fun given_whenRefresh_thenReturn200() {
            // Given
            val jwtToken = JwtToken(token = "token", tokenType = "refresh", email = USER_EMAIL, timeToLive = 10)
            whenever(methodCall = tokenProvider.findByTokenAndThrowException(token = "token")).thenReturn(jwtToken)
            whenever(methodCall = userService.findByEmail(email = jwtToken.email)).thenReturn(user)
            doNothing().`when`(tokenProvider).markLogoutEventForToken(email = jwtToken.email)

            val generatedToken = "generatedToken"
            whenever(methodCall = tokenProvider.generateJwtToken(email = eq(user.email!!), roles = anyList())).thenReturn(generatedToken)
            whenever(methodCall = tokenProvider.generateRefreshToken(email = eq(user.email!!), roles = anyList())).thenReturn(generatedToken)
            // When
            val response: UserWrapperResponse = authController.refresh(refreshTokenRequest = refreshTokenRequest, resultOfValidation = bindingResult)
            // Then
            assertNotNull(response)
            assertEquals(generatedToken, response.token)
            assertEquals(user.name, response.userResponse.name)
            assertEquals(user.email, response.userResponse.email)
            assertEquals(user.roles.size, response.userResponse.roles.size)
            assertEquals("generatedToken", response.token)
            assertEquals("generatedToken", response.refreshToken)
        }
    }

    @Nested
    internal inner class ActivateEmailTest {

        @Test
        fun givenSuccessfulPath_whenActivateEmail_thenReturn200() {
            // Given
            doNothing().`when`(userService).activateEmail(token = "token")
            whenever(methodCall = messageSourceService.get(code = "your_email_activated")).thenReturn("your_email_activated")
            // When
            val response: Map<String, String> = authController.activateEmail(token = "token")
            // Then
            assertNotNull(response)
            assertNotNull(response["message"])
            assertEquals("your_email_activated", response["message"])
        }
    }

    @Nested
    internal inner class ResendEmailActivationTest {

        @Test
        fun given_whenResendEmailActivationWithActivatedUser_thenThrowServerException() {
            // Given
            user.emailActivatedAt = Date()
            whenever(methodCall = userService.findByEmail(email = "email")).thenReturn(user)
            whenever(methodCall = messageSourceService.get(code = "this_email_already_activated")).thenReturn("this_email_already_activated")
            // When
            val closureToTest = Executable { authController.resendEmailActivation(email = "email") }
            // Then
            val assertThrows: ServerException = assertThrows(ServerException::class.java, closureToTest)
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows.statusCode)
            assertEquals(OmaErrorMessageType.BASIC_INVALID_INPUT, assertThrows.omaErrorMessageType)
        }

        @Test
        fun given_whenResendEmailActivation_thenReturn200() {
            // Given
            user.emailActivatedAt = null
            whenever(methodCall = userService.findByEmail(email = "email")).thenReturn(user)
            doNothing().`when`(eventPublisher).publishEvent(any<UserEmailActivationSendEvent>())
            whenever(methodCall = messageSourceService.get(code = "activation_email_sent")).thenReturn("activation_email_sent")
            // When
            val response: Map<String, String> = authController.resendEmailActivation(email = "email")
            // Then
            assertNotNull(response)
            assertNotNull(response["message"])
            assertEquals("activation_email_sent", response["message"])
        }
    }

    @Nested
    internal inner class ResetPasswordTest {

        @Test
        fun given_whenResendEmailActivation_thenReturn200() {
            // Given
            doNothing().`when`(userService).passwordReset(email = "email")
            whenever(methodCall = messageSourceService.get(code = "password_reset_link_sent")).thenReturn("password_reset_link_sent")
            // When
            val response: Map<String, String> = authController.resetPassword(email = "email")
            // Then
            assertNotNull(response)
            assertNotNull(response["message"])
            assertEquals("password_reset_link_sent", response["message"])
        }
    }

    @Nested
    internal inner class ChangePasswordTest {
        private lateinit var request: ChangePasswordRequest

        @Test
        fun given_whenResendEmailActivation_thenReturn200() {
            // Given
            request = ChangePasswordRequest(email = "email", password = "pass", passwordConfirmation = "pass")
            doNothing().`when`(userService).changePassword(request = request, token = "token")
            whenever(methodCall = messageSourceService.get(code = "password_changed_success")).thenReturn("password_changed_success")
            // When
            val response: Map<String, String> = authController.changePassword(request = request, token = "token")
            // Then
            assertNotNull(response)
            assertNotNull(response["message"])
            assertEquals("password_changed_success", response["message"])
        }
    }

    @Nested
    internal inner class LogoutTest {
        private val request: HttpServletRequest = mock<HttpServletRequest>()

        @Test
        fun given_whenLogout_thenDoesNotReturn() {
            // Given
            doReturn(value = user).`when`(userService).loggedInUser()
            doNothing().`when`(tokenProvider).markLogoutEventForToken(email = user.email!!)
            whenever(messageSourceService.get(code = "password_changed_success")).thenReturn("password_changed_success")
            // When
            val response = Executable { authController.logout(request = request) }
            // Then
            assertDoesNotThrow { response }
        }
    }
}
