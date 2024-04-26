package com.github.senocak.auth.controller

import com.github.senocak.auth.domain.PaginatedResponse
import com.github.senocak.auth.domain.PaginatedResponseMongoTemplate
import com.github.senocak.auth.domain.User
import com.github.senocak.auth.domain.dto.ExceptionDto
import com.github.senocak.auth.domain.dto.UpdateUserDto
import com.github.senocak.auth.domain.dto.UserPaginationDTO
import com.github.senocak.auth.domain.dto.UserResponse
import com.github.senocak.auth.domain.dto.UserWrapperResponse
import com.github.senocak.auth.exception.ServerException
import com.github.senocak.auth.security.Authorize
import com.github.senocak.auth.service.MessageSourceService
import com.github.senocak.auth.service.UserService
import com.github.senocak.auth.util.AppConstants.ADMIN
import com.github.senocak.auth.util.AppConstants.DEFAULT_PAGE_NUMBER
import com.github.senocak.auth.util.AppConstants.DEFAULT_PAGE_SIZE
import com.github.senocak.auth.util.AppConstants.SECURITY_SCHEME_NAME
import com.github.senocak.auth.util.AppConstants.USER
import com.github.senocak.auth.util.OmaErrorMessageType
import com.github.senocak.auth.util.convertEntityToDto
import com.github.senocak.auth.util.logger
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import org.slf4j.Logger
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.validation.BindingResult
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@Authorize(roles = [ADMIN, USER])
@RequestMapping(BaseController.V1_USER_URL)
@Tag(name = "User", description = "User Controller")
class UserController(
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder,
    private val messageSourceService: MessageSourceService
): BaseController() {
    private val log: Logger by logger()

    @Throws(ServerException::class)
    @Operation(
        summary = "Get me",
        tags = ["User"],
        responses = [
            ApiResponse(responseCode = "200", description = "successful operation",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = UserWrapperResponse::class)))),
            ApiResponse(responseCode = "500", description = "internal server error occurred",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class))))
        ],
        security = [SecurityRequirement(name = SECURITY_SCHEME_NAME, scopes = [ADMIN, USER])]
    )
    @GetMapping("/me")
    fun me(): UserResponse = userService.loggedInUser().convertEntityToDto()

    @PatchMapping("/me")
    @Operation(
            summary = "Update user by username",
            tags = ["User"],
            responses = [
                ApiResponse(responseCode = "200", description = "successful operation",
                        content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = HashMap::class)))),
                ApiResponse(responseCode = "500", description = "internal server error occurred",
                        content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class))))
            ],
            security = [SecurityRequirement(name = SECURITY_SCHEME_NAME, scopes = [ADMIN, USER])]
    )
    @Throws(ServerException::class)
    fun patchMe(request: HttpServletRequest,
        @Parameter(description = "Request body to update", required = true) @Validated @RequestBody userDto: UpdateUserDto,
        resultOfValidation: BindingResult
    ): UserResponse {
        validate(resultOfValidation = resultOfValidation)
        val user: User = userService.loggedInUser()
        val name: String? = userDto.name
        if (!name.isNullOrEmpty())
            user.name = name
        val password: String? = userDto.password
        val passwordConfirmation: String? = userDto.passwordConfirmation
        if (!password.isNullOrEmpty()) {
            if (passwordConfirmation.isNullOrEmpty()) {
                messageSourceService.get(code = "password_confirmation_not_provided")
                    .apply { log.error(this) }
                    .run { throw ServerException(omaErrorMessageType = OmaErrorMessageType.BASIC_INVALID_INPUT,
                        variables = arrayOf(this), statusCode = HttpStatus.BAD_REQUEST) }
            }
            if (passwordConfirmation != password) {
                messageSourceService.get(code = "password_and_confirmation_not_matched")
                    .apply { log.error(this) }
                    .run { throw ServerException(omaErrorMessageType = OmaErrorMessageType.BASIC_INVALID_INPUT,
                        variables = arrayOf(this), statusCode = HttpStatus.BAD_REQUEST) }
            }
            user.password = passwordEncoder.encode(password)
        }
        return userService.save(user = user)
            .run user@ {
                this@user.convertEntityToDto()
            }
    }

    @Throws(ServerException::class)
    @Authorize(roles = [ADMIN])
    @GetMapping(headers = ["X-API-VERSION=template"])
    @Operation(
        summary = "All Users",
        tags = ["User"],
        responses = [
            ApiResponse(responseCode = "200", description = MediaType.APPLICATION_JSON_VALUE,
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = UserPaginationDTO::class)))),
            ApiResponse(responseCode = "500", description = "internal server error occurred",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class))))
        ],
        security = [SecurityRequirement(name = SECURITY_SCHEME_NAME, scopes = [ADMIN])]
    )
    fun getUserByTemplate(
        @Parameter(name = "page", description = "Page number", example = DEFAULT_PAGE_NUMBER) @RequestParam(defaultValue = "0", required = false) page: Int,
        @Parameter(name = "size", description = "Page size", example = DEFAULT_PAGE_SIZE) @RequestParam(defaultValue = "\${spring.data.web.pageable.default-page-size:10}", required = false) size: Int,
        @Parameter(name = "q", description = "Search keyword", example = "lorem") @RequestParam(required = false) q: String?,
        @Parameter(name = "roleIds", description = "List of role ids", example = "12b9374e-4e52-4142-a1af-16144ef4a27d") @RequestParam roleIds: List<String>?,
        @Parameter(name = "startDate", description = "Date range start", example = "2024-01-15T00:00:00.000Z") @RequestParam startDate: String?,
        @Parameter(name = "endDate", description = "Date range end", example = "2024-03-15T23:59:59.999Z") @RequestParam endDate: String?,
        // @RequestParam filter: List<String>?,
        // name|regex|(?i)anil
        // email|eq|anil1@senocak.com
        // createdAt|gte|2024-04-11T00:00
    ): PaginatedResponseMongoTemplate<UserResponse> = run {
        val all = userService.createSpecificationForUserAndRetrieve(
            pageable = PageRequest.of(page, size),
            name = q,
            email = q,
            roleIds = roleIds?.map { UUID.fromString(it) },
            startDate = startDate,
            endDate = endDate,
        )
        /* Alternative
        val all: Page<User> = userService.findAllWithFilter(
            typeParameterClass = User::class.java,
            filtering = FilteringFactory.parseFromParams(filter = filter ?: arrayListOf(), typeParameterClass = User::class.java),
            pageable = pageable
        )
        PaginatedResponse<UserResponse>()
            .also {
                it.currentPage = all.number
                it.totalItems = all.totalElements
                it.totalPages = all.totalPages
                it.items = all.content.map { it.convertEntityToDto() }.toList()
                it.hasNext = all.hasNext()
            }
         */
        PaginatedResponseMongoTemplate<UserResponse>()
            .also {
                it.page = page
                it.size = size
                it.items = all.map { c -> c.convertEntityToDto() }.toList()
            }
    }

    @Throws(ServerException::class)
    @Authorize(roles = [ADMIN])
    @GetMapping(headers = ["X-API-VERSION=data"])
    @Operation(
        summary = "All Users",
        tags = ["User"],
        responses = [
            ApiResponse(responseCode = "200", description = MediaType.APPLICATION_JSON_VALUE,
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = UserPaginationDTO::class)))),
            ApiResponse(responseCode = "500", description = "internal server error occurred",
                content = arrayOf(Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ExceptionDto::class))))
        ],
        security = [SecurityRequirement(name = SECURITY_SCHEME_NAME, scopes = [ADMIN])]
    )
    fun getUserByData(
        @Parameter(name = "page", description = "Page number", example = DEFAULT_PAGE_NUMBER) @RequestParam(defaultValue = "0", required = false) page: Int,
        @Parameter(name = "size", description = "Page size", example = DEFAULT_PAGE_SIZE) @RequestParam(defaultValue = "\${spring.data.web.pageable.default-page-size:10}", required = false) size: Int,
        @Parameter(name = "q", description = "Search keyword", example = "lorem") @RequestParam(required = false) q: String?,
    ): PaginatedResponse<UserResponse> = run {
        val all: Page<User> = userService.createSpecificationForUser(
            q = q,
            pageable = PageRequest.of(page, size)
        )
        PaginatedResponse<UserResponse>()
            .also {
                it.currentPage = all.number
                it.totalItems = all.totalElements
                it.totalPages = all.totalPages
                it.items = all.content.map { c -> c.convertEntityToDto() }.toList()
                it.hasNext = all.hasNext()
            }
    }
}
