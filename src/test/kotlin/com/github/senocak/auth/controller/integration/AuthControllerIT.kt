package com.github.senocak.auth.controller.integration

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.senocak.auth.TestConstants.USER_EMAIL
import com.github.senocak.auth.TestConstants.USER_NAME
import com.github.senocak.auth.TestConstants.USER_PASSWORD
import com.github.senocak.auth.config.SpringBootTestConfig
import com.github.senocak.auth.controller.AuthController
import com.github.senocak.auth.controller.BaseController
import com.github.senocak.auth.domain.PasswordResetToken
import com.github.senocak.auth.domain.PasswordResetTokenRepository
import com.github.senocak.auth.domain.User
import com.github.senocak.auth.domain.dto.ChangePasswordRequest
import com.github.senocak.auth.domain.dto.LoginRequest
import com.github.senocak.auth.domain.dto.RefreshTokenRequest
import com.github.senocak.auth.domain.dto.RegisterRequest
import com.github.senocak.auth.domain.dto.UserWrapperResponse
import com.github.senocak.auth.exception.RestExceptionHandler
import com.github.senocak.auth.service.MessageSourceService
import com.github.senocak.auth.service.UserService
import com.github.senocak.auth.util.OmaErrorMessageType
import com.github.senocak.auth.util.randomStringGenerator
import java.util.UUID
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
import org.hamcrest.core.IsEqual.equalTo
import org.hamcrest.core.IsNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * This integration test class is written for
 * @see AuthController
 */
@SpringBootTestConfig
@DisplayName("Integration Tests for AuthController")
class AuthControllerIT {
    @Autowired private lateinit var authController: AuthController
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var messageSourceService: MessageSourceService
    @Autowired private lateinit var passwordResetTokenRepository: PasswordResetTokenRepository

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun beforeEach() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(RestExceptionHandler(messageSourceService = messageSourceService))
            .build()
        passwordResetTokenRepository.deleteAll()
    }

    @Nested
    @Order(1)
    @DisplayName("Test class for login scenarios")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    internal inner class LoginTest {
        private val request: LoginRequest = LoginRequest(email = "", password = "")

        @Test
        @Order(1)
        @DisplayName("ServerException is expected since request body is not valid")
        @Throws(Exception::class)
        fun givenInvalidSchema_whenLogin_thenThrowServerException() {
            // Given
            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeValueAsString(request))
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.statusCode",
                    equalTo(HttpStatus.BAD_REQUEST.value())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.id",
                    equalTo(OmaErrorMessageType.JSON_SCHEMA_VALIDATOR.messageId)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.text",
                    equalTo(OmaErrorMessageType.JSON_SCHEMA_VALIDATOR.text)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    hasSize<Any>(4)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    containsInAnyOrder("password: {not_blank}", "password: {min_max_length}",
                        "email: {min_max_length}", "email: {not_blank}")))
        }

        @Test
        @Order(2)
        @DisplayName("ServerException is expected since credentials are not valid")
        @Throws(Exception::class)
        fun givenInvalidCredentials_whenLogin_thenThrowServerException() {
            // Given
            request.email = "anil1@senocak.com"
            request.password = "not_asenocak"
            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeValueAsString(request))
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.statusCode",
                    equalTo(HttpStatus.UNAUTHORIZED.value())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.id",
                    equalTo(OmaErrorMessageType.UNAUTHORIZED.messageId)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.text",
                    equalTo(OmaErrorMessageType.UNAUTHORIZED.text)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    hasSize<Any>(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables[0]",
                    equalTo("Username or password invalid. AuthenticationCredentialsNotFoundException occurred for anil1")))
        }

        @Test
        @Order(3)
        @DisplayName("ServerException is expected since credentials are not valid")
        @Throws(Exception::class)
        fun givenNotActivatedUser_whenLogin_thenThrowServerException() {
            // Given
            request.email = "anil2@gmail.com"
            request.password = "asenocak"
            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeValueAsString(request))
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.statusCode",
                    equalTo(HttpStatus.UNAUTHORIZED.value())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.id",
                    equalTo(OmaErrorMessageType.UNAUTHORIZED.messageId)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.text",
                    equalTo(OmaErrorMessageType.UNAUTHORIZED.text)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    hasSize<Any>(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables[0]",
                    equalTo("Email not activated!")))
        }

        @Test
        @Order(4)
        @DisplayName("Happy path")
        @Throws(Exception::class)
        fun given_whenLogin_thenReturn200() {
            // Given
            request.email = "anil1@senocak.com"
            request.password = "asenocak"
            // When
            val perform: ResultActions = login(loginRequest = request)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.jsonPath("$.user.email", equalTo(request.email)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.user.roles", hasSize<Any>(2)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.token", IsNull.notNullValue()))
        }
    }

    @Nested
    @Order(2)
    @DisplayName("Test class for register scenarios")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    internal inner class RegisterTest {
        private val registerRequest: RegisterRequest = RegisterRequest(name = "", email = "", password = "")

        @Test
        @Order(1)
        @DisplayName("ServerException is expected since request body is not valid")
        @Throws(Exception::class)
        fun givenInvalidSchema_whenRegister_thenThrowServerException() {
            // Given
            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeValueAsString(registerRequest))
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.statusCode",
                    equalTo(HttpStatus.BAD_REQUEST.value())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.id",
                    equalTo(OmaErrorMessageType.JSON_SCHEMA_VALIDATOR.messageId)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.text",
                    equalTo(OmaErrorMessageType.JSON_SCHEMA_VALIDATOR.text)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    hasSize<Any>(6)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    containsInAnyOrder(
                        "password: {min_max_length}",
                        "password: Password must be 6 or more characters in length.\n" +
                                "Password must contain 1 or more uppercase characters.\n" +
                                "Password must contain 1 or more lowercase characters.\n" +
                                "Password must contain 1 or more digit characters.\n" +
                                "Password must contain 1 or more special characters.",
                        "email: Invalid email",
                        "password: {not_blank}",
                        "name: {not_blank}",
                        "name: {min_max_length}"
                    )))
        }

        @Test
        @Order(2)
        @DisplayName("ServerException is expected since there is already user with username")
        @Throws(Exception::class)
        fun givenEmailExist_whenRegister_thenThrowServerException() {
            // Given
            registerRequest.name = USER_NAME
            registerRequest.email = USER_EMAIL
            registerRequest.password = USER_PASSWORD
            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeValueAsString(registerRequest))
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.statusCode",
                    equalTo(HttpStatus.BAD_REQUEST.value())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.id",
                    equalTo(OmaErrorMessageType.JSON_SCHEMA_VALIDATOR.messageId)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.text",
                    equalTo(OmaErrorMessageType.JSON_SCHEMA_VALIDATOR.text)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    hasSize<Any>(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables[0]",
                    equalTo("Email is already using: $USER_EMAIL")))
        }

        @Test
        @Order(3)
        @DisplayName("Happy path")
        fun given_whenRegister_thenReturn201() {
            // Given
            registerRequest.name = USER_NAME
            registerRequest.email = "userNew@email.com"
            registerRequest.password = USER_PASSWORD
            // When
            val perform: ResultActions = register(registerRequest = registerRequest)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isCreated)
                .andExpect(MockMvcResultMatchers.jsonPath("$.message", IsNull.notNullValue()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                    equalTo("Please verify your email to login")))
        }
    }

    @Nested
    @Order(3)
    @DisplayName("Test class for refresh scenarios")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    internal inner class RefreshTest {
        private val request: RefreshTokenRequest = RefreshTokenRequest(token = "token")

        @Test
        @Order(1)
        @DisplayName("ServerException is expected since request body is not valid")
        @Throws(Exception::class)
        fun givenInvalidBody_whenRefresh_thenThrowServerException() {
            // Given
            request.token = "should be between 49 and 50"

            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeValueAsString(request))
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.statusCode",
                    equalTo(HttpStatus.BAD_REQUEST.value())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.id",
                    equalTo(OmaErrorMessageType.JSON_SCHEMA_VALIDATOR.messageId)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.text",
                    equalTo(OmaErrorMessageType.JSON_SCHEMA_VALIDATOR.text)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    hasSize<Any>(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    containsInAnyOrder("token: {min_max_length}")))
        }

        @Test
        @Order(2)
        @DisplayName("ServerException is expected since refresh token is not found")
        @Throws(Exception::class)
        fun givenInvalidTokenType_whenRefresh_thenThrowServerException() {
            // Given
            request.token = 50.randomStringGenerator()

            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeValueAsString(value = request))
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isNotFound)
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.statusCode",
                    equalTo(HttpStatus.NOT_FOUND.value())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.id",
                    equalTo(OmaErrorMessageType.NOT_FOUND.messageId)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.text",
                    equalTo(OmaErrorMessageType.NOT_FOUND.text)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    hasSize<Any>(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    containsInAnyOrder("Token is not found in redis")))
        }

        @Test
        @Order(3)
        @DisplayName("Happy path")
        fun given_whenRefresh_thenReturn200() {
            // Given
            val loginRequest = LoginRequest(email = "anil1@senocak.com", password = "asenocak")
            val loginResponse: UserWrapperResponse = login(loginRequest = loginRequest)
                .run { this.andReturn().response.contentAsString }
                .run { objectMapper.readValue(this, UserWrapperResponse::class.java) }

            request.token = loginResponse.refreshToken

            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeValueAsString(value = request))
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.jsonPath("$.user.email", equalTo(loginRequest.email)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.user.roles", hasSize<Any>(2)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.token", IsNull.notNullValue()))
        }
    }

    @Nested
    @Order(4)
    @DisplayName("Test class for activate email scenarios")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    internal inner class ActivateEmailTest {

        @Test
        @Order(1)
        @DisplayName("Happy path")
        fun given_whenActivateEmail_thenReturn200() {
            // Given
            val name: String = 10.randomStringGenerator()
            val registerRequest = RegisterRequest(name = name, email = "$name@gmail.com", password = USER_PASSWORD)
            register(registerRequest = registerRequest)
                .run { this.andReturn().response.contentAsString }
                .run { objectMapper.readValue(this, Map::class.java) }

            val createdUser: User = userService.findByEmail(email = registerRequest.email)

            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/activate-email/${createdUser.emailActivationToken}")
                .contentType(MediaType.APPLICATION_JSON)
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                    equalTo("Your e-mail activated successfully!")))
        }
    }

    @Nested
    @Order(5)
    @DisplayName("Test class for resend email activation scenarios")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    internal inner class ResendEmailActivationTest {

        @Test
        @Order(1)
        @DisplayName("ServerException is expected since user is not found")
        fun givenNotFoundUser_whenResendEmailActivation_thenThrowServerException() {
            // Given
            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/resend-email-activation/${10.randomStringGenerator()}@gmail.com")
                .contentType(MediaType.APPLICATION_JSON)
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isNotFound)
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.statusCode",
                    equalTo(HttpStatus.NOT_FOUND.value())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.id",
                    equalTo(OmaErrorMessageType.NOT_FOUND.messageId)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.text",
                    equalTo(OmaErrorMessageType.NOT_FOUND.text)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    hasSize<Any>(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    containsInAnyOrder("User not found!")))
        }

        @Test
        @Order(2)
        @DisplayName("ServerException is expected since user is already activated")
        fun givenAlreadyActivatedUser_whenResendEmailActivation_thenThrowServerException() {
            // Given
            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/resend-email-activation/$USER_EMAIL")
                .contentType(MediaType.APPLICATION_JSON)
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.statusCode",
                    equalTo(HttpStatus.BAD_REQUEST.value())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.id",
                    equalTo(OmaErrorMessageType.BASIC_INVALID_INPUT.messageId)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.text",
                    equalTo(OmaErrorMessageType.BASIC_INVALID_INPUT.text)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    hasSize<Any>(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    containsInAnyOrder("This e-mail is already activated!")))
        }

        @Test
        @Order(3)
        @DisplayName("Happy path")
        fun given_whenActivateEmail_thenReturn200() {
            // Given
            val name: String = 10.randomStringGenerator()
            val registerRequest = RegisterRequest(name = name, email = "$name@gmail.com", password = USER_PASSWORD)
            register(registerRequest = registerRequest)
                .run { this.andReturn().response.contentAsString }
                .run { objectMapper.readValue(this, Map::class.java) }

            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/resend-email-activation/${registerRequest.email}")
                .contentType(MediaType.APPLICATION_JSON)
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                    equalTo("Activation e-mail sent!")))
        }
    }

    @Nested
    @Order(6)
    @DisplayName("Test class for resend email activation scenarios")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    internal inner class ResetPasswordTest {

        @Test
        @Order(1)
        @DisplayName("ServerException is expected since user is not found")
        fun givenNotFoundUser_whenResetPassword_thenThrowServerException() {
            // Given
            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/reset-password/${10.randomStringGenerator()}@gmail.com")
                .contentType(MediaType.APPLICATION_JSON)
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isNotFound)
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.statusCode",
                    equalTo(HttpStatus.NOT_FOUND.value())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.id",
                    equalTo(OmaErrorMessageType.NOT_FOUND.messageId)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.text",
                    equalTo(OmaErrorMessageType.NOT_FOUND.text)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    hasSize<Any>(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    containsInAnyOrder("User not found!")))
        }

        @Test
        @Order(2)
        @DisplayName("Happy path")
        fun given_whenResetPassword_thenReturn200() {
            // Given
            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/reset-password/$USER_EMAIL")
                .contentType(MediaType.APPLICATION_JSON)
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                    equalTo("Password reset link sent")))
        }
    }

    @Nested
    @Order(7)
    @DisplayName("Test class for change password scenarios")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    internal inner class ChangePasswordTest {
        private lateinit var request: ChangePasswordRequest

        @Test
        @Order(1)
        @DisplayName("ServerException is expected since schema is not valid")
        fun givenNotValidSchema_whenChangePassword_thenThrowServerException() {
            // Given
            request = ChangePasswordRequest(email = "email", password = "pass", passwordConfirmation = "pass")
            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/change-password/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeValueAsString(value = request))
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity)
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.statusCode",
                    equalTo(HttpStatus.UNPROCESSABLE_ENTITY.value())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.id",
                    equalTo(OmaErrorMessageType.GENERIC_SERVICE_ERROR.messageId)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.text",
                    equalTo(OmaErrorMessageType.GENERIC_SERVICE_ERROR.text)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    hasSize<Any>(5)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    containsInAnyOrder(
                        "Validation error!",
                        "password: {min_max_length}",
                        "password: Password must be 6 or more characters in length.\n" +
                                "Password must contain 1 or more uppercase characters.\n" +
                                "Password must contain 1 or more digit characters.\n" +
                                "Password must contain 1 or more special characters.",
                        "passwordConfirmation: Password must be 6 or more characters in length.\n" +
                                "Password must contain 1 or more uppercase characters.\n" +
                                "Password must contain 1 or more digit characters.\n" +
                                "Password must contain 1 or more special characters.",
                        "email: {invalid_email}"
                    )))
        }

        @Test
        @Order(2)
        @DisplayName("ServerException is expected since token is not found")
        fun givenNotFoundToken_whenChangePassword_thenThrowServerException() {
            // Given
            request = ChangePasswordRequest(email = USER_EMAIL, password = USER_PASSWORD, passwordConfirmation = USER_PASSWORD)
            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/change-password/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeValueAsString(value = request))
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isNotFound)
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.statusCode",
                    equalTo(HttpStatus.NOT_FOUND.value())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.id",
                    equalTo(OmaErrorMessageType.NOT_FOUND.messageId)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.text",
                    equalTo(OmaErrorMessageType.NOT_FOUND.text)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    hasSize<Any>(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    containsInAnyOrder("PasswordReset Token is expired for token")))
        }

        @Test
        @Order(3)
        @DisplayName("ServerException is expected since user is not found")
        fun givenNotFoundUser_whenChangePassword_thenThrowServerException() {
            // Given
            val passwordResetToken: PasswordResetToken = PasswordResetToken(
                token = 10.randomStringGenerator(),
                userId = UUID.fromString("4cb9374e-4e52-4142-a1af-16144ef4a27d")
            )
                .run { passwordResetTokenRepository.save(this) }
            request = ChangePasswordRequest(email = "not_valid@email.com", password = USER_PASSWORD, passwordConfirmation = USER_PASSWORD)
            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/change-password/${passwordResetToken.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeValueAsString(value = request))
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isNotFound)
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.statusCode",
                    equalTo(HttpStatus.NOT_FOUND.value())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.id",
                    equalTo(OmaErrorMessageType.NOT_FOUND.messageId)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.text",
                    equalTo(OmaErrorMessageType.NOT_FOUND.text)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    hasSize<Any>(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    containsInAnyOrder("User not found!")))
        }

        @Test
        @Order(4)
        @DisplayName("ServerException is expected since token not belong to user")
        fun givenTokenNotBelongedUser_whenChangePassword_thenThrowServerException() {
            // Given
            val passwordResetToken: PasswordResetToken = PasswordResetToken(
                token = 10.randomStringGenerator(),
                userId = UUID.fromString("11111111-1111-1111-1111-111111111111")
            )
                .run { passwordResetTokenRepository.save(this) }
            request = ChangePasswordRequest(email = USER_EMAIL, password = USER_PASSWORD, passwordConfirmation = USER_PASSWORD)
            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/change-password/${passwordResetToken.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeValueAsString(value = request))
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.statusCode",
                    equalTo(HttpStatus.BAD_REQUEST.value())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.id",
                    equalTo(OmaErrorMessageType.BASIC_INVALID_INPUT.messageId)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.error.text",
                    equalTo(OmaErrorMessageType.BASIC_INVALID_INPUT.text)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    hasSize<Any>(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.exception.variables",
                    containsInAnyOrder("PasswordReset Token is invalid")))
        }

        @Test
        @Order(5)
        @DisplayName("Happy path")
        fun given_whenChangePassword_thenReturn200() {
            // Given
            val findByEmail = userService.findByEmail(email = "anil1@senocak.com")
            val passwordResetToken: PasswordResetToken = PasswordResetToken(
                token = 10.randomStringGenerator(),
                userId = findByEmail.id!!
            )
                .run { passwordResetTokenRepository.save(this) }
            request = ChangePasswordRequest(email = findByEmail.email!!, password = "stanford.Pollich15", passwordConfirmation = "stanford.Pollich15")
            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/change-password/${passwordResetToken.token}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeValueAsString(value = request))
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                    equalTo("Password changed successfully")))
        }
    }

    @Nested
    @Order(8)
    @DisplayName("Test class for logout scenarios")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    internal inner class LogoutTest {

        @Test
        @Order(1)
        @DisplayName("Happy path")
        fun given_whenChangePassword_thenReturn200() {
            // Given
            val loginRequest = LoginRequest(email = USER_EMAIL, password = "stanford.Pollich15")
            login(loginRequest = loginRequest)
                .run { this.andReturn().response.contentAsString }
                .run { objectMapper.readValue(this, UserWrapperResponse::class.java) }

            val requestBuilder: RequestBuilder = MockMvcRequestBuilders
                .post("${BaseController.V1_AUTH_URL}/logout")
                .contentType(MediaType.APPLICATION_JSON)
            // When
            val perform: ResultActions = mockMvc.perform(requestBuilder)
            // Then
            perform.andExpect(MockMvcResultMatchers.status().isNoContent)
        }
    }

    /**
     * @param value -- an object that want to be serialized
     * @return -- string
     * @throws JsonProcessingException -- throws JsonProcessingException
     */
    @Throws(JsonProcessingException::class)
    private fun writeValueAsString(value: Any): String = objectMapper.writeValueAsString(value)

    private fun login(loginRequest: LoginRequest): ResultActions =
        MockMvcRequestBuilders
            .post("${BaseController.V1_AUTH_URL}/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(writeValueAsString(loginRequest))
            .run { mockMvc.perform(this) }

    private fun register(registerRequest: RegisterRequest): ResultActions =
        MockMvcRequestBuilders
            .post("${BaseController.V1_AUTH_URL}/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(writeValueAsString(registerRequest))
            .run { mockMvc.perform(this) }
}
