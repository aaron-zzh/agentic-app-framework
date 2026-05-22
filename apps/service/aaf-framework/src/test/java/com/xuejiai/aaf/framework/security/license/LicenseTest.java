package com.xuejiai.aaf.framework.security.license;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LicenseTest {

    @BeforeEach
    @AfterEach
    void resetLicense() {
        License.get().reset();
    }

    @Test
    @DisplayName("Given 默认状态 When 获取 License Then isPremium=false, tier=free")
    void should_have_free_defaults() {
        // 断言
        assertThat(License.get().isPremium()).isFalse();
        assertThat(License.get().getUserId()).isNull();
        assertThat(License.get().getTier()).isEqualTo("free");
        assertThat(License.get().getExpiresAt()).isNull();
    }

    @Test
    @DisplayName("Given 调用 activate When 获取 License Then isPremium=true 且字段已填充")
    void should_activate_license() {
        // 准备参数
        var exp = Instant.now().plusSeconds(3600);

        // 调用
        License.get().activate("user-001", "enterprise", exp);

        // 断言
        assertThat(License.get().isPremium()).isTrue();
        assertThat(License.get().getUserId()).isEqualTo("user-001");
        assertThat(License.get().getTier()).isEqualTo("enterprise");
        assertThat(License.get().getExpiresAt()).isEqualTo(exp);
    }

    @Test
    @DisplayName("Given 已激活 When reset Then 恢复为免费模式")
    void should_reset_to_free() {
        // 准备参数
        License.get().activate("user-001", "pro", Instant.now().plusSeconds(3600));

        // 调用
        License.get().reset();

        // 断言
        assertThat(License.get().isPremium()).isFalse();
        assertThat(License.get().getTier()).isEqualTo("free");
    }
}
