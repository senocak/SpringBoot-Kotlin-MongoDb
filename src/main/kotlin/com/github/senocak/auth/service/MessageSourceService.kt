package com.github.senocak.auth.service

import com.github.senocak.auth.util.logger
import java.util.Locale
import org.slf4j.Logger
import org.springframework.context.MessageSource
import org.springframework.context.NoSuchMessageException
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Service

@Service
class MessageSourceService(
    private val messageSource: MessageSource
){
    private val log: Logger by logger()

    @JvmOverloads
    fun get(
        code: String,
        params: Array<Any?>? = arrayOfNulls(0),
        locale: Locale? = LocaleContextHolder.getLocale()
    ): String =
        try {
            messageSource.getMessage(code, params, locale!!)
        } catch (e: NoSuchMessageException) {
            log.warn("Translation message not found ({}): {}", locale, code)
            code
        }
}
