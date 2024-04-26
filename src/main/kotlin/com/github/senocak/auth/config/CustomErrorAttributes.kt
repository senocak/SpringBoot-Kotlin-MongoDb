package com.github.senocak.auth.config

import com.github.senocak.auth.domain.dto.ExceptionDto
import com.github.senocak.auth.util.OmaErrorMessageType
import com.github.senocak.auth.util.logger
import org.slf4j.Logger
import org.springframework.boot.web.error.ErrorAttributeOptions
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.WebRequest
import jakarta.servlet.RequestDispatcher

@Configuration
@Profile("!integration-test")
class CustomErrorAttributes : DefaultErrorAttributes() {
    private val log: Logger by logger()

    override fun getErrorAttributes(webRequest: WebRequest, options: ErrorAttributeOptions): Map<String, Any> {
        val errorAttributes = super.getErrorAttributes(webRequest, options)
        val errorMessage: Any? = webRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE, RequestAttributes.SCOPE_REQUEST)
        val exceptionDto = ExceptionDto()
        if (errorMessage != null) {
            val omaErrorMessageType = OmaErrorMessageType.NOT_FOUND
            exceptionDto.statusCode = errorAttributes["status"] as Int
            exceptionDto.variables = arrayOf(errorAttributes["error"].toString(), errorAttributes["message"].toString())
            exceptionDto.error = ExceptionDto.OmaErrorMessageTypeDto(omaErrorMessageType.messageId, omaErrorMessageType.text)
        }
        return HashMap<String, Any>()
                .also { it["exception"] = exceptionDto }
                .also { log.warn("Exception occurred in DefaultErrorAttributes: $it") }
    }
}