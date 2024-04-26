package com.github.senocak.auth.service

import com.github.senocak.auth.domain.MailConfigurationProperties
import com.github.senocak.auth.domain.User
import com.github.senocak.auth.util.logger
import jakarta.mail.MessagingException
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine

@Async
@Service
class EmailService(
    private val emailSender: JavaMailSender,
    private val mailConfigurationProperties: MailConfigurationProperties,
    private val templateEngine: SpringTemplateEngine,
    private val messageSourceService: MessageSourceService
){
    private val log: Logger by logger()
    @Value("\${spring.application.name}") private lateinit var appName: String
    @Value("\${server.port}") private lateinit var port: String
    @Value("\${app.frontend-url}") private lateinit var frontendUrl: String

    /**
     * Send an e-mail to the specified address.
     * @param to      Address who receive
     * @param subject String subject
     * @param text    String message
     * @throws MessagingException when sending fails
     */
    @Throws(MessagingException::class)
    private fun send(to: InternetAddress, subject: String, text: String) {
        val mimeMessage: MimeMessage = emailSender.createMimeMessage()
        val mimeMessageHelper = MimeMessageHelper(mimeMessage, true)
        mimeMessageHelper.setFrom(mailConfigurationProperties.username)
        mimeMessageHelper.setTo(to)
        mimeMessageHelper.setSubject(subject)
        mimeMessageHelper.setText(text, true)
        emailSender.send(mimeMessage)
    }

    /**
     * Send user email activation link.
     *
     * @param user User
     */
    fun sendUserEmailActivation(user: User, emailActivationToken: String) {
        try {
            log.info("[EmailService] Sending activation e-mail: ${user.id} - ${user.email} - $emailActivationToken")
            val ctx: Context = context
                .also { it: Context ->
                    it.setVariable("name", user.name)
                    it.setVariable("email", user.email)
                    it.setVariable("url", "${frontendUrl}/auth/activate-email/$emailActivationToken")
                    it.setVariable("token", emailActivationToken)
                }
            send(to = InternetAddress(user.email, user.name), subject = messageSourceService.get(code = "email_activation"),
                text = templateEngine.process("mail/user-email-activation", ctx))
            log.info("[EmailService] Sent activation e-mail: ${user.id} - ${user.email} - $emailActivationToken")
        } catch (e: Exception) {
            log.error("[EmailService] Failed to send activation e-mail: ${e.message}")
        }
    }

    /**
     * Send reset password email.
     * @param user User
     * @param token String
     */
    fun sendResetPasswordEmail(user: User, token: String) {
        try {
            log.info("[EmailService] Sending password reset e-mail: ${user.id} - ${user.email} - $token")
            val ctx: Context = context
                .also { it: Context ->
                    it.setVariable("name", user.name)
                    it.setVariable("email", user.email)
                    it.setVariable("url", "${frontendUrl}/auth/change-password/${token}")
                    it.setVariable("token", token)
                }
            send(to = InternetAddress(user.email, user.name), subject = messageSourceService.get(code = "password_change"),
                text = templateEngine.process("mail/reset-password", ctx))
            log.info("[EmailService] Sent password reset e-mail: ${user.id} - ${user.email} - $token")
        } catch (e: Exception) {
            log.error("[EmailService] Failed to send password reset e-mail: ${e.message}")
        }
    }

    /**
     * Send change password success email.
     * @param user User
     */
    fun sendChangePasswordSuccess(user: User) {
        try {
            log.info("[EmailService] Sending change password e-mail: ${user.id} - ${user.email}")
            val ctx: Context = context
                .also { it: Context ->
                    it.setVariable("name", user.name)
                    it.setVariable("email", user.email)
                }
            send(to = InternetAddress(user.email, user.name), subject = messageSourceService.get(code = "password_changed_success"),
                text = templateEngine.process("mail/password-changed-success.html", ctx))
            log.info("[EmailService] Sent change password e-mail: ${user.id} - ${user.email}")
        } catch (e: Exception) {
            log.error("[EmailService] Failed to send change password e-mail: ${e.message}")
        }
    }

    /**
     * Create context for template engine.
     * @return Context
     */
    private val context: Context
        get() =
            Context(LocaleContextHolder.getLocale())
                .apply { this.setVariable("APP_NAME", appName) }

}