package com.xuejiai.aaf.framework.security.license;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LicenseAwareConfigTest {

    private final LicenseAwareConfig config = new LicenseAwareConfig();

    @BeforeEach
    @AfterEach
    void resetLicense() {
        License.get().reset();
    }

    @Test
    @DisplayName("Given premium=false When 获取配置 Then maxTokens=2048, maxConcurrentAgents=3")
    void should_return_free_limits_when_not_premium() {
        // 断言
        assertThat(config.getMaxTokens()).isEqualTo(2048);
        assertThat(config.getMaxConcurrentAgents()).isEqualTo(3);
    }

    @Test
    @DisplayName("Given premium=true When 获取配置 Then maxTokens=8192, maxConcurrentAgents=20")
    void should_return_premium_limits_when_premium() {
        // 准备参数
        License.get().activate("user-1", "pro", Instant.now().plusSeconds(3600));

        // 断言
        assertThat(config.getMaxTokens()).isEqualTo(8192);
        assertThat(config.getMaxConcurrentAgents()).isEqualTo(20);
    }
}
