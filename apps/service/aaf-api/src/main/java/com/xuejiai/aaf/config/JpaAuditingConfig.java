package com.xuejiai.aaf.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** JPA 审计配置，启用 @CreatedDate / @LastModifiedDate 自动填充。 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
