package com.xuejiai.aaf.framework.messaging.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import com.xuejiai.aaf.framework.messaging.ChannelSender;

/** 邮件服务自动配置。 */
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
@ConditionalOnClass(JavaMailSender.class)
public class EmailAutoConfiguration {

    @Bean
    public EmailSender springEmailSender(
            JavaMailSender mailSender,
            EmailProperties properties,
            ApplicationEventPublisher eventPublisher) {
        return new SpringEmailSender(mailSender, properties, eventPublisher);
    }

    @Bean
    public ChannelSender emailChannelSender(EmailSender emailSender) {
        return new EmailChannelSender(emailSender);
    }
}
