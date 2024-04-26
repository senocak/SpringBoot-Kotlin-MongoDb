package com.github.senocak.auth.util.validation

import com.github.senocak.auth.util.logger
import jakarta.validation.Constraint
import kotlin.reflect.KClass
import org.slf4j.Logger
import java.util.regex.Matcher
import java.util.regex.Pattern
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [EmailValidator::class])
annotation class ValidEmail (
    val message: String = "Invalid email",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Any>> = []
)

class EmailValidator : ConstraintValidator<ValidEmail?, String?> {
    private val log: Logger by logger()

    override fun initialize(constraintAnnotation: ValidEmail?) {
        log.info("EmailValidator initialized")
    }

    override fun isValid(email: String?, context: ConstraintValidatorContext): Boolean =
        when (email) {
            null -> false
            else -> {
                val pattern: Pattern = Pattern.compile(
                    "^[_A-Za-z0-9-+]" +
                            "(.[_A-Za-z0-9-]+)*@" + "[A-Za-z0-9-]+(.[A-Za-z0-9]+)*" + "(.[A-Za-z]{2,})$"
                )
                val matcher: Matcher = pattern.matcher(email)
                matcher.matches()
            }
        }
}