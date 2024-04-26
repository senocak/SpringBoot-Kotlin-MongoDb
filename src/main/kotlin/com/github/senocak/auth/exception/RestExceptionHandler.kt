package com.github.senocak.auth.exception

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.github.senocak.auth.domain.dto.ExceptionDto
import com.github.senocak.auth.service.MessageSourceService
import com.github.senocak.auth.util.OmaErrorMessageType
import com.github.senocak.auth.util.logger
import jakarta.validation.ConstraintViolationException
import java.lang.reflect.UndeclaredThrowableException
import java.security.InvalidParameterException
import java.util.function.Consumer
import org.slf4j.Logger
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingPathVariableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class RestExceptionHandler(
    private val messageSourceService: MessageSourceService
){
    private val log: Logger by logger()

    @ExceptionHandler(
        BadCredentialsException::class,
        ConstraintViolationException::class,
        InvalidParameterException::class,
        TypeMismatchException::class,
        MissingPathVariableException::class,
        HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class,
        MismatchedInputException::class,
        UndeclaredThrowableException::class
    )
    fun handleBadRequestException(ex: Exception): ResponseEntity<Any> =
        generateResponseEntity(httpStatus = HttpStatus.BAD_REQUEST,
            variables = arrayOf(messageSourceService.get(code = "malformed_json_request"), ex.message),
            omaErrorMessageType = OmaErrorMessageType.BASIC_INVALID_INPUT)

    @ExceptionHandler(
        AccessDeniedException::class,
        AuthenticationCredentialsNotFoundException::class,
        com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException::class
    )
    fun handleUnAuthorized(ex: Exception): ResponseEntity<Any> =
        generateResponseEntity(httpStatus = HttpStatus.UNAUTHORIZED, variables = arrayOf(ex.message),
            omaErrorMessageType = OmaErrorMessageType.UNAUTHORIZED)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleHttpRequestMethodNotSupported(ex: HttpRequestMethodNotSupportedException): ResponseEntity<Any> =
        generateResponseEntity(httpStatus = HttpStatus.METHOD_NOT_ALLOWED,
            variables = arrayOf(messageSourceService.get(code = "method_not_supported"), ex.message),
            omaErrorMessageType = OmaErrorMessageType.EXTRA_INPUT_NOT_ALLOWED)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleHttpMediaTypeNotSupported(ex: HttpMediaTypeNotSupportedException): ResponseEntity<Any> =
        generateResponseEntity(httpStatus = HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            omaErrorMessageType = OmaErrorMessageType.BASIC_INVALID_INPUT, variables = arrayOf(ex.message))

    @ExceptionHandler(
        NoHandlerFoundException::class,
        UsernameNotFoundException::class,
        NoResourceFoundException::class
    )
    fun handleNoHandlerFoundException(ex: Exception): ResponseEntity<Any> =
        generateResponseEntity(httpStatus = HttpStatus.NOT_FOUND,
            omaErrorMessageType = OmaErrorMessageType.NOT_FOUND, variables = arrayOf(ex.message))

    @ExceptionHandler(BindException::class)
    fun handleBindException(ex: BindException): ResponseEntity<Any> =
        arrayListOf(messageSourceService.get(code = "validation_error"))
            .apply {
                ex.bindingResult.allErrors.forEach(Consumer { error: ObjectError ->
                    this.add(element = "${(error as FieldError).field}: ${error.defaultMessage}")
                })
            }
            .run {
                generateResponseEntity(httpStatus = HttpStatus.UNPROCESSABLE_ENTITY,
                    variables = this.toTypedArray(), omaErrorMessageType = OmaErrorMessageType.GENERIC_SERVICE_ERROR)
            }

    @ExceptionHandler(ServerException::class)
    fun handleServerException(ex: ServerException): ResponseEntity<Any> =
        generateResponseEntity(httpStatus = ex.statusCode, omaErrorMessageType = ex.omaErrorMessageType, variables = ex.variables)

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<Any> =
        generateResponseEntity(httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
            variables = arrayOf(messageSourceService.get(code = "server_error"), ex.message),
            omaErrorMessageType = OmaErrorMessageType.GENERIC_SERVICE_ERROR)

    /**
     * @param httpStatus -- returned code
     * @return -- returned body
     */
    private fun generateResponseEntity(
        httpStatus: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
        omaErrorMessageType: OmaErrorMessageType,
        variables: Array<String?>
    ): ResponseEntity<Any> =
        log.error("Exception is handled. HttpStatus: $httpStatus, OmaErrorMessageType: $omaErrorMessageType, variables: ${variables.toList()}")
            .run { ExceptionDto() }
            .apply {
                this.statusCode = httpStatus.value()
                this.error = ExceptionDto.OmaErrorMessageTypeDto(id = omaErrorMessageType.messageId, text = omaErrorMessageType.text)
                this.variables = variables
            }
            .run { ResponseEntity.status(httpStatus).body(this) }
}
