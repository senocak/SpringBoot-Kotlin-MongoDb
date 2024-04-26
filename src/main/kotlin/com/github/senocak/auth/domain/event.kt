package com.github.senocak.auth.domain

import org.springframework.context.ApplicationEvent

class UserEmailActivationSendEvent(source: Any, val user: User) : ApplicationEvent(source)
