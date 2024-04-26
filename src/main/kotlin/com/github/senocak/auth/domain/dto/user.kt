package com.github.senocak.auth.domain.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.github.senocak.auth.domain.User
import com.github.senocak.auth.util.RoleName
import com.github.senocak.auth.util.validation.Password
import com.github.senocak.auth.util.validation.PasswordMatches
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page

@JsonPropertyOrder("name", "username", "email", "roles", "resourceUrl")
class UserResponse(
    @JsonProperty("name")
    @Schema(example = "Lorem Ipsum", description = "Name of the user", required = true, name = "name", type = "String")
    var name: String,

    @Schema(example = "lorem@ipsum.com", description = "Email of the user", required = true, name = "email", type = "String")
    var email: String,

    @ArraySchema(schema = Schema(example = "ROLE_USER", description = "Roles of the user", required = true, name = "roles"))
    var roles: List<String>,

    @Schema(example = "1253123123", description = "Email activation datetime", required = false, name = "emailActivatedAt", type = "Long")
    var emailActivatedAt: Long? = null
): BaseDto()

@PasswordMatches
data class UpdateUserDto(
    @Schema(example = "Anil", description = "Name", required = true, name = "name", type = "String")
    @field:Size(min = 4, max = 40)
    var name: String? = null,

    @Schema(example = "Anil123", description = "Password", required = true, name = "password", type = "String")
    @field:Password(message = "{invalid_password}", detailedMessage = true)
    var password: String? = null,

    @JsonProperty("password_confirmation")
    @Schema(example = "Anil123", description = "Password confirmation", required = true, name = "password", type = "String")
    @field:Password(message = "{invalid_password}", detailedMessage = true)
    var passwordConfirmation: String? = null
): BaseDto()

class UserPaginationDTO(
    pageModel: Page<User>,
    items: List<UserResponse>,
    sortBy: String? = null,
    sort: String? = null
): PaginationResponse<User, UserResponse>(page = pageModel, items = items, sortBy = sortBy, sort = sort)