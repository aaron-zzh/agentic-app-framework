package com.xuejiai.aaf.framework.security.license;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** License 标识与验签配置入口。M32：{@link LicenseProperties} 提供可配置/可轮换的受信公钥。 */
@Configuration
@EnableConfigurationProperties({LicenseIdentityProperties.class, LicenseProperties.class})
public class LicenseIdentityConfig {}
