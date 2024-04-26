package com.github.senocak.auth.service

import com.github.senocak.auth.domain.FilterableRepository
import com.github.senocak.auth.domain.Filtering
import com.github.senocak.auth.domain.PasswordResetToken
import com.github.senocak.auth.domain.PasswordResetTokenRepository
import com.github.senocak.auth.domain.User
import com.github.senocak.auth.domain.UserRepository
import com.github.senocak.auth.domain.dto.ChangePasswordRequest
import com.github.senocak.auth.exception.ServerException
import com.github.senocak.auth.util.OmaErrorMessageType
import com.github.senocak.auth.util.RoleName
import com.github.senocak.auth.util.logger
import com.github.senocak.auth.util.randomStringGenerator
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID
import org.bson.types.ObjectId
import org.slf4j.Logger
import org.springframework.data.domain.Example
import org.springframework.data.domain.ExampleMatcher
import org.springframework.data.domain.ExampleMatcher.GenericPropertyMatchers.contains
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.http.HttpStatus
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val mongoTemplate: MongoTemplate,
    private val userRepository: UserRepository,
    private val messageSourceService: MessageSourceService,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val emailService: EmailService,
    private val passwordEncoder: PasswordEncoder
): UserDetailsService, FilterableRepository<User> {
    private val log: Logger by logger()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    override fun findAllWithFilter(typeParameterClass: Class<User>, filtering: Filtering, pageable: Pageable): Page<User> {
        val query = constructQueryFromFiltering(filtering = filtering).with(pageable)
        val ts = mongoTemplate.find(query, typeParameterClass)
        return PageableExecutionUtils.getPage(ts, pageable) { mongoTemplate.count(query, typeParameterClass) }
    }

    override fun getAllPossibleValuesForFilter(typeParameterClass: Class<User>, filtering: Filtering, filterKey: String): List<Any> {
        val query: Query = constructQueryFromFiltering(filtering = filtering)
        return mongoTemplate.query(typeParameterClass).distinct(filterKey).matching(query).all()
    }

    fun findAllUsers(): MutableList<User> = userRepository.findAll()

    /**
     * @param id -- uuid to find in db
     * @return -- Optional User object
     */
    fun findById(id: UUID): User =
        userRepository.findById(id).orElseThrow { UsernameNotFoundException(messageSourceService.get(code = "user_not_found")) }

    fun findByIdTemplate(id: UUID): User = run{
        val query = Query(Criteria.where("_id").`is`(ObjectId(id.toString())))
        mongoTemplate.findOne(query, User::class.java)
            ?: throw UsernameNotFoundException(messageSourceService.get(code = "user_not_found"))
    }

    fun createSpecificationForUserAndRetrieve(
        pageable: Pageable = PageRequest.of(0, 1),
        name: String? = null,
        email: String? = null,
        roleIds: List<UUID>? = null,
        startDate: String? = null,
        endDate: String? = null,
    ): MutableList<User> =
        run {
            val listOfCriteria = arrayListOf<Criteria>()
            if(name != null) listOfCriteria.add(element = Criteria.where("name").regex("(?i)$name"))
            if(email != null) listOfCriteria.add(element = Criteria.where("email").regex("(?i)$email"))
            if(roleIds != null) listOfCriteria.add(element = Criteria.where("roles").`in`(roleIds))
            if(startDate != null || endDate != null) {
                var criteriaDate = Criteria.where("createdAt")
                if(startDate != null)
                    criteriaDate = criteriaDate.gte(LocalDateTime.parse(startDate, formatter))
                if(endDate != null)
                    criteriaDate = criteriaDate.lte(LocalDateTime.parse(endDate, formatter))
                listOfCriteria.add(element = criteriaDate)
            }

            val criteria = Criteria()
                .also {
                    if (listOfCriteria.size > 0) it.orOperator(listOfCriteria)
                }
            val query = Query(criteria).with(pageable)
            //mongoTemplate.count(query, User.class)
            mongoTemplate.find(query, User::class.java, "users")
            //mongoTemplate.query(User::class.java).matching(query).all()
        }

    fun createSpecificationForUser(q: String?, pageable: Pageable): Page<User> {
        val user = User().also {
            it.name = q?.lowercase()
            it.email = q?.lowercase()
        }
        val matcher = ExampleMatcher.matchingAny()
            .withIgnoreNullValues()
            .withIgnorePaths("password")
            .withMatcher("name", contains().ignoreCase())
            .withMatcher("email") { match -> match.contains().ignoreCase() }
        val of = Example.of(user, matcher)
        return userRepository.findAll(of, pageable)
    }

    /**
     * @param email -- string email to find in db
     * @return -- true or false
     */
    fun existsByEmail(email: String): Boolean =
        userRepository.existsByEmail(email = email)

    /**
     * @param email -- string email to find in db
     * @return -- User object
     * @throws UsernameNotFoundException -- throws UsernameNotFoundException
     */
    @Throws(UsernameNotFoundException::class)
    fun findByEmail(email: String): User =
        userRepository.findByEmail(email = email) ?: throw UsernameNotFoundException(messageSourceService.get(code = "user_not_found"))

    /**
     * @param user -- User object to persist to db
     * @return -- User object that is persisted to db
     */
    fun save(user: User): User = userRepository.save(user)

    /**
     * @param email -- id
     * @return -- Spring User object
     */
    @Transactional
    @Throws(UsernameNotFoundException::class)
    override fun loadUserByUsername(email: String): org.springframework.security.core.userdetails.User {
        val user: User = findByEmail(email = email)
        val authorities: List<GrantedAuthority> = user.roles.stream()
            .map { SimpleGrantedAuthority(RoleName.fromString(r = it)!!.name) }
            .toList()
        return org.springframework.security.core.userdetails.User(user.email, user.password, authorities)
    }

    /**
     * @return -- User entity that is retrieved from db
     * @throws ServerException -- throws ServerException
     */
    @Throws(ServerException::class)
    fun loggedInUser(): User =
        (SecurityContextHolder.getContext().authentication.principal as org.springframework.security.core.userdetails.User).username
            .run { findByEmail(email = this) }

    /**
     * Activate user's email by token.
     * @param token String
     */
    fun activateEmail(token: String) {
        (userRepository.findByEmailActivationToken(token = token)
            ?: throw UsernameNotFoundException(messageSourceService.get(code = "user_not_found"))
        )
            .also { it.emailActivatedAt = Date() }
            .run { userRepository.save(this) }
    }

    /**
     * PasswordReset user passwordReset from request.
     * @param email email
     */
    fun passwordReset(email: String) {
        val user: User = findByEmail(email = email)
        val byUserId: PasswordResetToken? = passwordResetTokenRepository.findByUserId(userId = user.id!!)
        if (byUserId != null) {
            messageSourceService.get(code = "password_reset_token_exist")
                .apply { log.error(this) }
                .run { throw ServerException(omaErrorMessageType = OmaErrorMessageType.BASIC_INVALID_INPUT,
                    statusCode = HttpStatus.CONFLICT, variables = arrayOf(this)) }
        }
        val token: String = 50.randomStringGenerator()
        PasswordResetToken(token = token, userId = user.id!!)
            .run { passwordResetTokenRepository.save(this) }
        emailService.sendResetPasswordEmail(user = user, token = token)
    }

    /**
     * Changes the user's password from request.
     * @param request ChangePasswordRequest.
     * @param token   String.
     */
    fun changePassword(request: ChangePasswordRequest, token: String) {
        val passwordResetToken: PasswordResetToken = passwordResetTokenRepository.findByToken(token = token)
             ?: messageSourceService.get(code = "password_reset_token_expired", params = arrayOf(token))
                 .apply { log.error(this) }
                 .run { throw ServerException(omaErrorMessageType = OmaErrorMessageType.NOT_FOUND,
                     statusCode = HttpStatus.NOT_FOUND, variables = arrayOf(this)) }

        var user: User = findByEmail(email = request.email)
        if (passwordResetToken.userId != user.id)
            messageSourceService.get(code = "invalid_token_for_mail")
                .apply { log.error(this) }
                .run { throw ServerException(omaErrorMessageType = OmaErrorMessageType.BASIC_INVALID_INPUT,
                    statusCode = HttpStatus.BAD_REQUEST, variables = arrayOf(this)) }

        if (request.password != request.passwordConfirmation)
            messageSourceService.get(code = "password_mismatch")
                .apply { log.error(this) }
                .run { throw ServerException(omaErrorMessageType = OmaErrorMessageType.BASIC_INVALID_INPUT,
                    statusCode = HttpStatus.BAD_REQUEST, variables = arrayOf(this)) }

        if (passwordEncoder.matches(request.password, user.password))
            messageSourceService.get(code = "new_password_must_be_different_from_old")
                .apply { log.error(this) }
                .run { throw ServerException(omaErrorMessageType = OmaErrorMessageType.BASIC_INVALID_INPUT,
                    statusCode = HttpStatus.CONFLICT, variables = arrayOf(this)) }
        user.password = passwordEncoder.encode(request.password)
        user = userRepository.save(user)
        passwordResetTokenRepository.delete(passwordResetToken)
        emailService.sendChangePasswordSuccess(user)
    }
}
