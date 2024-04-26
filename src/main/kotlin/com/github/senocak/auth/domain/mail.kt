package com.github.senocak.auth.domain

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app.mail")
class MailConfigurationProperties{
    lateinit var host: String
    lateinit var port: String
    lateinit var protocol: String
    lateinit var username: String
    lateinit var password: String
}