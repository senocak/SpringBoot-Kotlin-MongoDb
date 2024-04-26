package com.github.senocak.auth.security

import com.github.senocak.auth.domain.User
import com.github.senocak.auth.service.UserService
import com.github.senocak.auth.util.RoleName
import com.github.senocak.auth.util.logger
import org.slf4j.Logger
import org.slf4j.MDC
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CustomAuthenticationManager(
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder
): AuthenticationManager {
    private val log: Logger by logger()

    @Transactional
    override fun authenticate(authentication: Authentication): Authentication {
        val user: User = userService.findByEmail(email = authentication.name)
        if (authentication.credentials != null){
            val matches: Boolean = passwordEncoder.matches(authentication.credentials.toString(), user.password)
            if (!matches) {
                "Username or password invalid. AuthenticationCredentialsNotFoundException occurred for ${user.name}"
                    .apply { log.error(this) }
                    .run { throw AuthenticationCredentialsNotFoundException(this) }
            }
        }
        val authorities: MutableCollection<SimpleGrantedAuthority> = ArrayList()
        authorities.add(element = SimpleGrantedAuthority(RoleName.ROLE_USER.role))
        if (user.roles.stream().anyMatch { it == RoleName.ROLE_ADMIN.role })
            authorities.add(element = SimpleGrantedAuthority(RoleName.ROLE_ADMIN.role))

        val loadUserByUsername: org.springframework.security.core.userdetails.User = userService.loadUserByUsername(authentication.name)
        val auth: Authentication = UsernamePasswordAuthenticationToken(loadUserByUsername, user.password, authorities)
        SecurityContextHolder.getContext().authentication = auth
        MDC.put("userId", "${user.id}")
        log.info("Authentication is set to SecurityContext for ${user.name}")
        return auth
    }
}