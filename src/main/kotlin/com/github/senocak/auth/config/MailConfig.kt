package com.github.senocak.auth.config

import com.github.senocak.auth.domain.MailConfigurationProperties
import java.nio.charset.StandardCharsets
import java.util.Properties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import org.thymeleaf.templateresolver.ITemplateResolver

@Configuration
class MailConfig(
    private val mailConfigurationProperties: MailConfigurationProperties
){
    @Value("\${app.mail.smtp.socketFactory.port}") private val socketPort = 0
    @Value("\${app.mail.smtp.auth}") private val auth = false
    @Value("\${app.mail.smtp.starttls.enable}") private val starttls = false
    @Value("\${app.mail.smtp.starttls.required}") private val startllsRequired = false
    @Value("\${app.mail.smtp.socketFactory.fallback}") private val fallback = false

    /**
     * Defining JavaMailSender as a bean
     * JavaMailSender is an interface for JavaMail, supporting MIME messages both as direct arguments
     * and through preparation callbacks
     * @return -- an implementation of the JavaMailSender interface
     */
    @Bean
    fun javaMailSender(): JavaMailSender =
            JavaMailSenderImpl()
                .also {ms ->
                    ms.javaMailProperties = Properties()
                            .also {p ->
                                p["mail.smtp.auth"] = auth
                                p["mail.smtp.starttls.enable"] = starttls
                                p["mail.smtp.starttls.required"] = startllsRequired
                                p["mail.smtp.socketFactory.port"] = socketPort
                                p["mail.smtp.debug"] = "true"
                                p["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
                                p["mail.smtp.socketFactory.fallback"] = fallback
                            }
                    ms.host = mailConfigurationProperties.host
                    ms.port = mailConfigurationProperties.port.toInt()
                    ms.protocol = mailConfigurationProperties.protocol
                    ms.username = mailConfigurationProperties.username
                    ms.password = mailConfigurationProperties.password
                }

    /**
     * THYMELEAF TemplateResolver(3) <- TemplateEngine
     */
    @Bean(name = ["htmlTemplateEngine"])
    fun htmlTemplateEngine(): ITemplateEngine =
            SpringTemplateEngine()
                .also {
                    it.addTemplateResolver(htmlTemplateResolver())
                    it.setTemplateEngineMessageSource(templateMessageSource())
                }

    /**
     * THYMELEAF TemplateResolver(3) <- TemplateEngine
     * @return -- an implementation of the ITemplateResolver interface
     */
    private fun htmlTemplateResolver(): ITemplateResolver =
        ClassLoaderTemplateResolver()
                .also {
                    it.order = 2
                    it.prefix = "/templates/"
                    it.suffix = ".html"
                    it.templateMode = TemplateMode.HTML
                    it.characterEncoding = StandardCharsets.UTF_8.name()
                    it.isCacheable = false
                }

    /**
     * THYMELEAF TemplateMessageSource
     * @return -- an implementation of the TemplateMessageSource interface
     */
    private fun templateMessageSource(): ResourceBundleMessageSource =
        ResourceBundleMessageSource().also { it.setBasename("templates/i18n/Template") }
}