package com.xuejiai.aaf.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.xuejiai.aaf.framework.security.OperatorContext;

/** JPA 审计配置，启用 @CreatedDate / @LastModifiedDate / @CreatedBy / @LastModifiedBy 自动填充。 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<Long> auditorAware(OperatorContext operatorContext) {
        return operatorContext::currentOperatorId;
    }
}
