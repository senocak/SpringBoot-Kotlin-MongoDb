package com.github.senocak.auth.controller

import com.github.senocak.auth.TestConstants
import com.github.senocak.auth.createTestUser
import com.github.senocak.auth.domain.User
import com.github.senocak.auth.domain.dto.UpdateUserDto
import com.github.senocak.auth.domain.dto.UserResponse
import com.github.senocak.auth.exception.ServerException
import com.github.senocak.auth.service.MessageSourceService
import com.github.senocak.auth.service.UserService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.function.Executable
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.validation.BindingResult
import jakarta.servlet.http.HttpServletRequest
import org.mockito.InjectMocks
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.kotlin.mock

@Tag("unit")
@ExtendWith(MockitoExtension::class)
@DisplayName("Unit Tests for UserController")
class UserControllerTest {
    @InjectMocks lateinit var userController: UserController
    private val userService: UserService = mock<UserService>()
    private val passwordEncoder: PasswordEncoder = mock<PasswordEncoder>()
    private val messageSourceService: MessageSourceService = mock<MessageSourceService>()
    private val bindingResult: BindingResult = mock<BindingResult>()
    private val user: User = createTestUser()

    @Nested
    internal inner class GetMeTest {
        
        @Test
        @Throws(ServerException::class)
        fun givenServerException_whenGetMe_thenThrowServerException() {
            // Given
            doThrow(toBeThrown = ServerException::class).`when`(userService).loggedInUser()
            // When
            val closureToTest = Executable { userController.me() }
            // Then
            assertThrows(ServerException::class.java, closureToTest)
        }

        @Test
        @Throws(ServerException::class)
        fun given_whenGetMe_thenReturn200() {
            // Given
            doReturn(value = user).`when`(userService).loggedInUser()
            // When
            val getMe: UserResponse = userController.me()
            // Then
            assertNotNull(getMe)
            assertEquals(user.email, getMe.email)
            assertEquals(user.name, getMe.name)
        }
    }

    @Nested
    internal inner class PatchMeTest {
        private val updateUserDto: UpdateUserDto = UpdateUserDto()
        private val httpServletRequest: HttpServletRequest = Mockito.mock(HttpServletRequest::class.java)

        @Test
        @Throws(ServerException::class)
        fun givenNullPasswordConf_whenPatchMe_thenThrowServerException() {
            // Given
            doReturn(value = user).`when`(userService).loggedInUser()
            updateUserDto.password = "pass1"
            // When
            val closureToTest = Executable { userController.patchMe(httpServletRequest, updateUserDto, bindingResult) }
            // Then
            assertThrows(ServerException::class.java, closureToTest)
        }

        @Test
        @Throws(ServerException::class)
        fun givenInvalidPassword_whenPatchMe_thenThrowServerException() {
            // Given
            doReturn(value = user).`when`(userService).loggedInUser()
            updateUserDto.password = "pass1"
            updateUserDto.passwordConfirmation = "pass2"
            // When
            val closureToTest = Executable { userController.patchMe(httpServletRequest, updateUserDto, bindingResult) }
            // Then
            assertThrows(ServerException::class.java, closureToTest)
        }

        @Test
        @Throws(ServerException::class)
        fun given_whenPatchMe_thenThrowServerException() {
            // Given
            doReturn(value = user).`when`(userService).loggedInUser()
            updateUserDto.name = TestConstants.USER_NAME
            updateUserDto.password = "pass1"
            updateUserDto.passwordConfirmation = "pass1"
            doReturn(value = "pass1").`when`(passwordEncoder).encode("pass1")
            doReturn(value = user).`when`(userService).save(user = user)
            // When
            val patchMe: UserResponse = userController.patchMe(httpServletRequest, updateUserDto, bindingResult)
            // Then
            assertNotNull(patchMe)
            assertEquals(user.email, patchMe.email)
            assertEquals(user.name, patchMe.name)
        }
    }
}