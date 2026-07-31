package com.xuejiai.aaf.framework.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    @DisplayName("Given 精确公开路由 When 检查白名单 Then 不包含文件、AGUI、聊天与裸支付通知")
    void should_not_expose_protected_routes_when_check_public_paths() {
        var allPaths =
                List.of(
                        String.join("\n", SecurityConfig.PRODUCT_PUBLIC_PATHS),
                        String.join("\n", SecurityConfig.PUBLIC_GET_PATHS),
                        String.join("\n", SecurityConfig.PUBLIC_POST_PATHS));
        var joined = String.join("\n", allPaths);

        assertThat(joined)
                .doesNotContain("/api/auth/**")
                .doesNotContain("/api/system/files/**")
                .doesNotContain("/api/agui/run/**")
                .doesNotContain("/api/system/chat/sessions/thread/*/messages")
                .doesNotContain("/api/pay/orders/notify\n")
                .doesNotContain("/api/webhook/trigger/**");
        assertThat(SecurityConfig.PUBLIC_POST_PATHS)
                .contains(
                        "/api/channel/webhook/inbound",
                        "/api/pay/orders/notify/wx",
                        "/api/pay/orders/notify/alipay",
                        "/api/auth/oauth/exchange");
    }

    @Test
    @DisplayName("Given 少于 32 字节的 JWT secret When 创建密钥 Then 启动失败")
    void should_reject_jwt_secret_when_shorter_than_32_bytes() {
        var properties = new JwtProperties("too-short", 1, 1, "issuer", "audience");

        assertThatThrownBy(() -> securityConfig.jwtSecretKey(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少需要 32 字节");
    }

    @Test
    @DisplayName("Given 含多字节字符且达到 32 字节的 JWT secret When 创建密钥 Then 使用 UTF-8 字节")
    void should_use_utf8_bytes_when_create_jwt_secret() {
        var secret = "安全密钥安全密钥安全密钥安全密钥";
        var properties = new JwtProperties(secret, 1, 1, "issuer", "audience");

        var key = securityConfig.jwtSecretKey(properties);

        assertThat(key.getEncoded()).isEqualTo(secret.getBytes(StandardCharsets.UTF_8));
    }
}
