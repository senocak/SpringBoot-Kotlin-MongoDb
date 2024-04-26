package com.github.senocak.auth.domain.dto

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

@JsonPropertyOrder("statusCode", "error", "variables")
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("exception")
class ExceptionDto : BaseDto() {
    var statusCode = 200
    var error: OmaErrorMessageTypeDto? = null
    var variables: Array<String?> = arrayOf(String())

    @JsonPropertyOrder("id", "text")
    class OmaErrorMessageTypeDto(val id: String? = null, val text: String? = null)
}
