package com.github.senocak.auth.util.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import kotlin.reflect.KClass
import org.passay.CharacterRule
import org.passay.EnglishCharacterData
import org.passay.LengthRule
import org.passay.PasswordData
import org.passay.PasswordValidator
import org.passay.RuleResult
import org.passay.WhitespaceRule

@MustBeDocumented
@Constraint(validatedBy = [PasswordConstraintsValidator::class])
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.ANNOTATION_CLASS)
@Retention(
    AnnotationRetention.RUNTIME
)
annotation class Password(
    val message: String = "Invalid password.",
    val detailedMessage: Boolean = false,
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Any>> = []
)

class PasswordConstraintsValidator : ConstraintValidator<Password?, String?> {
    private var detailedMessage = false

    override fun initialize(constraintAnnotation: Password?) {
        super.initialize(constraintAnnotation)
        if (constraintAnnotation?.detailedMessage != null)
            detailedMessage = constraintAnnotation.detailedMessage
    }

    override fun isValid(password: String?, context: ConstraintValidatorContext): Boolean =
        when (password) {
            null ->  true
            else -> {
                val validator = PasswordValidator(
                    arrayListOf(
                        LengthRule(6, 32), // Length rule. Min 6 max 32 characters
                        CharacterRule(EnglishCharacterData.UpperCase, 1), // At least one upper case letter
                        CharacterRule(EnglishCharacterData.LowerCase, 1), // At least one lower case letter
                        CharacterRule(EnglishCharacterData.Digit, 1), // At least one number
                        CharacterRule(EnglishCharacterData.Special, 1), // At least one special characters
                        WhitespaceRule() // No whitespace
                    )
                )

                val result: RuleResult = validator.validate(PasswordData(password))
                when {
                    result.isValid -> true
                    else -> {
                            if (detailedMessage) {
                                val messages: List<String> = validator.getMessages(result)
                                context.buildConstraintViolationWithTemplate(messages.joinToString(separator = "\n"))
                                    .addConstraintViolation()
                                    .disableDefaultConstraintViolation()
                            }
                        false
                    }
                }
            }
        }
}
