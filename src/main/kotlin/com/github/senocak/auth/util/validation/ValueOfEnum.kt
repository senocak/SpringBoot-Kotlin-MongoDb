package com.github.senocak.auth.util.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import kotlin.reflect.KClass

@MustBeDocumented
@Constraint(validatedBy = [ValueOfEnumValidator::class])
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FIELD,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPE_PARAMETER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class ValueOfEnum(
    val enumClass: KClass<out Enum<*>>,
    val message: String = "Invalid value!",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Any>> = []
)

class ValueOfEnumValidator : ConstraintValidator<ValueOfEnum?, CharSequence?> {
    private var acceptedValues: List<String>? = null

    fun initialize(annotation: ValueOfEnum) {
        acceptedValues = annotation.enumClass.java.enumConstants
            .map { it.name }
            .toList()
    }

    override fun isValid(value: CharSequence?, context: ConstraintValidatorContext?): Boolean =
        when (value) {
            null -> true
            else -> acceptedValues!!.contains(value.toString())
        }
}
