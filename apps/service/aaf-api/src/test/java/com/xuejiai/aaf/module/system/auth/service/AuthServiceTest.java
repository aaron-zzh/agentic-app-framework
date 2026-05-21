package com.xuejiai.aaf.module.system.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageRequest;
import com.xuejiai.aaf.framework.messaging.MessageService;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.framework.security.JwtUtils;
import com.xuejiai.aaf.module.system.auth.vo.SendCodeDTO;
import com.xuejiai.aaf.module.system.user.repository.UserOauthRepository;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AuthServiceTest extends BaseMockitoUnitTest {

    @Mock private UserRepository userRepository;
    @Mock private UserOauthRepository userOauthRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private ActorContext actorContext;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private JwtDecoder jwtDecoder;
    @Mock private MessageService messageService;

    @InjectMocks private AuthService authService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        org.springframework.test.util.ReflectionTestUtils.setField(
                authService, "companyName", "学记智能");
        // OAuthClient List 字段手动注入（@InjectMocks 不处理泛型 List）
        org.springframework.test.util.ReflectionTestUtils.setField(
                authService, "oauthClients", List.of());
    }

    @Test
    @DisplayName("Given 有效邮箱 When sendCode Then 验证码存入 Redis 并发送邮件")
    void should_store_code_in_redis_and_send_email_when_send_code() {
        // 准备参数
        var dto = new SendCodeDTO("test@example.com", "register");

        // 调用
        authService.sendCode(dto);

        // 断言：验证码存入 Redis（5分钟）
        verify(valueOps)
                .set(
                        eq("verify_code:register:test@example.com"),
                        anyString(),
                        eq(java.time.Duration.ofMinutes(5)));

        // 断言：邮件通过 MessageService 发送
        var captor = ArgumentCaptor.forClass(MessageRequest.class);
        verify(messageService).send(captor.capture());
        var request = captor.getValue();
        assertThat(request.channel()).isEqualTo(MessageChannel.EMAIL);
        assertThat(request.templateCode()).isEqualTo("AUTH_VERIFY_CODE");
        assertThat(request.recipients()).containsExactly("test@example.com");
        assertThat(request.variables()).containsEntry("companyName", "学记智能");
        assertThat(request.variables()).containsEntry("expireMinutes", 5);
        assertThat(request.subject()).contains("学记智能");
    }

    @Test
    @DisplayName("Given MessageService 发送失败 When sendCode Then 不抛异常（降级）")
    void should_not_throw_when_email_send_fails() {
        // 准备参数
        var dto = new SendCodeDTO("test@example.com", "login");

        // mock 邮件发送失败
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP 连接失败"))
                .when(messageService)
                .send(any());

        // 调用 + 断言：不抛异常
        org.assertj.core.api.Assertions.assertThatCode(() -> authService.sendCode(dto))
                .doesNotThrowAnyException();

        // 验证码仍然存入了 Redis
        verify(valueOps).set(anyString(), anyString(), any());
    }
}
