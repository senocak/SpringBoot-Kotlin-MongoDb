package com.github.senocak.auth.util.validation

import com.github.senocak.auth.domain.dto.UpdateUserDto
import com.github.senocak.auth.util.logger
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import kotlin.reflect.KClass
import org.slf4j.Logger

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PasswordMatchesValidator::class])
annotation class PasswordMatches(
    val message: String = "Passwords don't match",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Any>> = []
)

class PasswordMatchesValidator : ConstraintValidator<PasswordMatches, Any> {
    private val log: Logger by logger()

    override fun initialize(passwordMatches: PasswordMatches) {
        log.info("PasswordMatchesValidator initialized")
    }

    override fun isValid(obj: Any, context: ConstraintValidatorContext): Boolean =
        when (obj.javaClass) {
            UpdateUserDto::class.java -> {
                val (_: String?, password: String?, passwordConfirmation: String?) = obj as UpdateUserDto
                val isMatched: Boolean = password == passwordConfirmation
                if (!isMatched) {
                    context.disableDefaultConstraintViolation()
                    context.buildConstraintViolationWithTemplate(context.defaultConstraintMessageTemplate)
                        .addConstraintViolation()
                }
                isMatched
            }
            else -> false
        }
}