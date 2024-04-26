package com.github.senocak.auth.exception

import com.github.senocak.auth.domain.dto.ExceptionDto
import com.github.senocak.auth.service.MessageSourceService
import com.github.senocak.auth.util.OmaErrorMessageType
import java.util.ArrayList
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.servlet.NoHandlerFoundException
import java.util.Arrays
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.InjectMocks
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.validation.BindException
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError

@Tag("unit")
@ExtendWith(MockitoExtension::class)
@DisplayName("Unit Tests for RestExceptionHandler")
class RestExceptionHandlerTest {
    @InjectMocks lateinit var restExceptionHandler: RestExceptionHandler
    private val messageSourceService: MessageSourceService = mock<MessageSourceService>()

    @Test
    fun givenException_whenHandleBadRequestException_thenAssertResult() {
        // Given
        doReturn(value = "malformed_json_request").`when`(messageSourceService).get(code = "malformed_json_request")
        val ex: Exception = BadCredentialsException("lorem")
        // When
        val handleBadRequestException: ResponseEntity<Any> = restExceptionHandler.handleBadRequestException(ex = ex)
        val exceptionDto: ExceptionDto? = handleBadRequestException.body as ExceptionDto?
        // Then
        assertNotNull(exceptionDto)
        assertEquals(HttpStatus.BAD_REQUEST, handleBadRequestException.statusCode)
        assertEquals(HttpStatus.BAD_REQUEST.value(), exceptionDto!!.statusCode)
        assertEquals(OmaErrorMessageType.BASIC_INVALID_INPUT.messageId, exceptionDto.error!!.id)
        assertEquals(OmaErrorMessageType.BASIC_INVALID_INPUT.text, exceptionDto.error!!.text)
        assertEquals(2, exceptionDto.variables.size)
        assertEquals("malformed_json_request", exceptionDto.variables[0])
        assertEquals(ex.message, exceptionDto.variables[1])
    }

    @Test
    fun givenException_whenHandleUnAuthorized_thenAssertResult() {
        // Given
        val ex: RuntimeException = AccessDeniedException("lorem")
        // When
        val handleBadRequestException: ResponseEntity<Any> = restExceptionHandler.handleUnAuthorized(ex = ex)
        val exceptionDto: ExceptionDto? = handleBadRequestException.body as ExceptionDto?
        // Then
        assertNotNull(exceptionDto)
        assertEquals(HttpStatus.UNAUTHORIZED, handleBadRequestException.statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED.value(), exceptionDto!!.statusCode)
        assertEquals(OmaErrorMessageType.UNAUTHORIZED.messageId, exceptionDto.error!!.id)
        assertEquals(OmaErrorMessageType.UNAUTHORIZED.text, exceptionDto.error!!.text)
        assertEquals(1, exceptionDto.variables.size)
        val message: Optional<String?> = Arrays.stream(exceptionDto.variables).findFirst()
        assertTrue(message.isPresent)
        assertEquals(ex.message, message.get())
    }

    @Test
    fun givenException_whenHandleHttpRequestMethodNotSupported_thenAssertResult() {
        // Given
        val ex = HttpRequestMethodNotSupportedException("lorem")
        whenever(methodCall = messageSourceService.get(code = "method_not_supported")).thenReturn("method_not_supported")
        // When
        val handleBadRequestException: ResponseEntity<Any> = restExceptionHandler.handleHttpRequestMethodNotSupported(ex = ex)
        val exceptionDto: ExceptionDto? = handleBadRequestException.body as ExceptionDto?
        // Then
        assertNotNull(exceptionDto)
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, handleBadRequestException.statusCode)
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED.value(), exceptionDto!!.statusCode)
        assertEquals(OmaErrorMessageType.EXTRA_INPUT_NOT_ALLOWED.messageId, exceptionDto.error!!.id)
        assertEquals(OmaErrorMessageType.EXTRA_INPUT_NOT_ALLOWED.text, exceptionDto.error!!.text)
        assertNotNull(exceptionDto.variables)
        assertEquals(2, exceptionDto.variables.size)
        assertEquals("method_not_supported", exceptionDto.variables[0])
        assertEquals(ex.message, exceptionDto.variables[1])
    }

    @Test
    fun givenException_whenHandleHttpMediaTypeNotSupported_thenAssertResult() {
        // Given
        val ex = HttpMediaTypeNotSupportedException("lorem")
        // When
        val handleBadRequestException: ResponseEntity<Any> = restExceptionHandler.handleHttpMediaTypeNotSupported(ex = ex)
        val exceptionDto: ExceptionDto? = handleBadRequestException.body as ExceptionDto?
        // Then
        assertNotNull(exceptionDto)
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, handleBadRequestException.statusCode)
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), exceptionDto!!.statusCode)
        assertEquals(OmaErrorMessageType.BASIC_INVALID_INPUT.messageId, exceptionDto.error!!.id)
        assertEquals(OmaErrorMessageType.BASIC_INVALID_INPUT.text, exceptionDto.error!!.text)
        assertEquals(1, exceptionDto.variables.size)
        val message: Optional<String?> = Arrays.stream(exceptionDto.variables).findFirst()
        assertTrue(message.isPresent)
        assertEquals(ex.message, message.get())
    }

    @Test
    fun givenException_whenHandleNoHandlerFoundException_thenAssertResult() {
        // Given
        val ex = NoHandlerFoundException("GET", "", HttpHeaders())
        // When
        val handleBadRequestException: ResponseEntity<Any> = restExceptionHandler.handleNoHandlerFoundException(ex = ex)
        val exceptionDto: ExceptionDto? = handleBadRequestException.body as ExceptionDto?
        // Then
        assertNotNull(exceptionDto)
        assertEquals(HttpStatus.NOT_FOUND, handleBadRequestException.statusCode)
        assertEquals(HttpStatus.NOT_FOUND.value(), exceptionDto!!.statusCode)
        assertEquals(OmaErrorMessageType.NOT_FOUND.messageId, exceptionDto.error!!.id)
        assertEquals(OmaErrorMessageType.NOT_FOUND.text, exceptionDto.error!!.text)
        assertEquals(1, exceptionDto.variables.size)
        val message: Optional<String?> = Arrays.stream(exceptionDto.variables).findFirst()
        assertTrue(message.isPresent)
        assertEquals("No endpoint GET .", message.get())
    }

    @Test
    fun givenException_whenHandleBindException_thenAssertResult() {
        // Given
        whenever(methodCall = messageSourceService.get(code = "validation_error")).thenReturn("validation_error")
        val bindException: BindException = mock<BindException>()
        val bindingResult: BindingResult = mock<BindingResult>()
        whenever(methodCall = bindException.bindingResult).thenReturn(bindingResult)

        val objectError = FieldError("name", "message", "default")
        val getAllErrors: ArrayList<ObjectError> = arrayListOf(objectError)
        whenever(methodCall = bindingResult.allErrors).thenReturn(getAllErrors)
        // When
        val handleBadRequestException: ResponseEntity<Any> = restExceptionHandler.handleBindException(ex = bindException)
        val exceptionDto: ExceptionDto? = handleBadRequestException.body as ExceptionDto?
        // Then
        assertNotNull(exceptionDto)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, handleBadRequestException.statusCode)
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), exceptionDto!!.statusCode)
        assertEquals(OmaErrorMessageType.GENERIC_SERVICE_ERROR.messageId, exceptionDto.error!!.id)
        assertEquals(OmaErrorMessageType.GENERIC_SERVICE_ERROR.text, exceptionDto.error!!.text)
        assertNotNull(exceptionDto.variables)
        assertEquals(2, exceptionDto.variables.size)
        assertEquals("validation_error", exceptionDto.variables[0])
        assertEquals("message: default", exceptionDto.variables[1])
    }

    @Test
    fun givenException_whenHandleServerException_thenAssertResult() {
        // Given
        val errrMsg = "lorem"
        val ex = ServerException(OmaErrorMessageType.NOT_FOUND, arrayOf(errrMsg), HttpStatus.CONFLICT)
        // When
        val handleBadRequestException: ResponseEntity<Any> = restExceptionHandler.handleServerException(ex = ex)
        val exceptionDto: ExceptionDto? = handleBadRequestException.body as ExceptionDto?
        // Then
        assertNotNull(exceptionDto)
        assertEquals(HttpStatus.CONFLICT, handleBadRequestException.statusCode)
        assertEquals(HttpStatus.CONFLICT.value(), exceptionDto!!.statusCode)
        assertEquals(OmaErrorMessageType.NOT_FOUND.messageId, exceptionDto.error!!.id)
        assertEquals(OmaErrorMessageType.NOT_FOUND.text, exceptionDto.error!!.text)
        assertEquals(1, exceptionDto.variables.size)
        val message: Optional<String?> = Arrays.stream(exceptionDto.variables).findFirst()
        assertTrue(message.isPresent)
        assertEquals(errrMsg, message.get())
    }

    @Test
    fun givenException_whenHandleGeneralException_thenAssertResult() {
        // Given
        whenever(methodCall = messageSourceService.get(code = "server_error")).thenReturn("server_error")
        val ex = Exception("lorem")
        // When
        val handleBadRequestException: ResponseEntity<Any> = restExceptionHandler.handleGeneralException(ex)
        val exceptionDto: ExceptionDto? = handleBadRequestException.body as ExceptionDto?
        // Then
        assertNotNull(exceptionDto)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, handleBadRequestException.statusCode)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), exceptionDto!!.statusCode)
        assertEquals(OmaErrorMessageType.GENERIC_SERVICE_ERROR.messageId, exceptionDto.error!!.id)
        assertEquals(OmaErrorMessageType.GENERIC_SERVICE_ERROR.text, exceptionDto.error!!.text)
        assertNotNull(exceptionDto.variables.size)
        assertEquals(2, exceptionDto.variables.size)
        assertEquals("server_error", exceptionDto.variables[0])
        assertEquals(ex.message, exceptionDto.variables[1])
    }
}
