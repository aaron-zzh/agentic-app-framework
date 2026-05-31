package com.xuejiai.aaf.framework.security.license;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** License 标识配置入口。 */
@Configuration
@EnableConfigurationProperties(LicenseIdentityProperties.class)
public class LicenseIdentityConfig {}
