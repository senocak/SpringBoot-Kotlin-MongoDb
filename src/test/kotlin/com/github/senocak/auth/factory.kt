package com.github.senocak.auth

import com.github.senocak.auth.TestConstants.USER_EMAIL
import com.github.senocak.auth.TestConstants.USER_NAME
import com.github.senocak.auth.TestConstants.USER_PASSWORD
import com.github.senocak.auth.domain.User
import com.github.senocak.auth.util.RoleName
import java.util.ArrayList
import java.util.Date

fun createTestUser(): User =
    User(name = USER_NAME, email = USER_EMAIL, password = USER_PASSWORD)
        .also {
            it.roles = arrayListOf<String>()
                .also { list: ArrayList<String> -> list.add(element = RoleName.ROLE_USER.role) }
                .also { list: ArrayList<String> -> list.add(element = RoleName.ROLE_ADMIN.role) }
            it.emailActivatedAt = Date()
        }