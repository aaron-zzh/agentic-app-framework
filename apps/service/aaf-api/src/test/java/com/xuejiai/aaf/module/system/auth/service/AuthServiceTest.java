package com.xuejiai.aaf.module.system.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
import com.xuejiai.aaf.framework.security.JwtUtils;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.auth.vo.AuthLoginDTO;
import com.xuejiai.aaf.module.system.auth.vo.SendCodeDTO;
import com.xuejiai.aaf.module.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.system.role.repository.RoleRepository;
import com.xuejiai.aaf.module.system.role.repository.UserRoleRepository;
import com.xuejiai.aaf.module.system.user.domain.User;
import com.xuejiai.aaf.module.system.user.repository.UserOauthRepository;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AuthServiceTest extends BaseMockitoUnitTest {

    @Mock private UserRepository userRepository;
    @Mock private UserOauthRepository userOauthRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private OperatorContext operatorContext;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private JwtDecoder jwtDecoder;
    @Mock private MessageService messageService;
    @Mock private SystemConfigService systemConfigService;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;

    @InjectMocks private AuthService authService;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(
                authService, "companyName", "学记智能");
        // OAuthClient List 字段手动注入（@InjectMocks 不处理泛型 List）
        org.springframework.test.util.ReflectionTestUtils.setField(
                authService, "oauthClients", List.of());
    }

    @Test
    @DisplayName("Given 用户名 When login Then 按用户名查询并登录")
    void should_login_by_username() {
        var user = activeUser();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "encoded-password")).thenReturn(true);
        stubTokenGeneration();

        var result = authService.login(new AuthLoginDTO("testuser", "pass123"), "device-1");

        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(userRepository).findByUsername("testuser");
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository).save(user);
        verify(jwtUtils).saveSession(1L, "device-1", "refresh-token");
    }

    @Test
    @DisplayName("Given 邮箱 When login Then 按邮箱查询并登录")
    void should_login_by_email() {
        var user = activeUser();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "encoded-password")).thenReturn(true);
        stubTokenGeneration();

        var result = authService.login(new AuthLoginDTO("test@example.com", "pass123"), "device-1");

        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(userRepository).findByEmail("test@example.com");
        verify(userRepository, never()).findByUsername(anyString());
        verify(userRepository).save(user);
        verify(jwtUtils).saveSession(1L, "device-1", "refresh-token");
    }

    @Test
    @DisplayName("Given 有效邮箱 When sendCode Then 验证码存入 Redis 并发送邮件")
    void should_store_code_in_redis_and_send_email_when_send_code() {
        stubVerifyCodeConfig();
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
        assertThat(request.templateCode()).isEqualTo("auth.verify_code.register");
        assertThat(request.recipients()).containsExactly("test@example.com");
        assertThat(request.variables()).containsEntry("expireMinutes", 5);
    }

    @Test
    @DisplayName("Given MessageService 发送失败 When sendCode Then 抛出异常（通知用户重试）")
    void should_throw_when_email_send_fails() {
        stubVerifyCodeConfig();
        // 准备参数
        var dto = new SendCodeDTO("test@example.com", "login");

        // mock 邮件发送失败
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP 连接失败"))
                .when(messageService)
                .send(any());

        // 调用 + 断言：异常向上抛出（让用户感知失败并重试，不静默降级）
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> authService.sendCode(dto))
                .isInstanceOf(RuntimeException.class);

        // 验证码已在发信失败前写入 Redis
        verify(valueOps).set(eq("verify_code:login:test@example.com"), anyString(), any());
    }

    private void stubTokenGeneration() {
        when(jwtUtils.generateToken(eq(1L), any())).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtUtils.getAccessTokenExpiresTime()).thenReturn(LocalDateTime.now().plusHours(1));
        when(userRoleRepository.findByUserIdAndDeletedFalse(1L)).thenReturn(List.of());
    }

    private void stubVerifyCodeConfig() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(systemConfigService.getInteger("security.verify_code_expire", 5)).thenReturn(5);
    }

    private User activeUser() {
        var user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPassword("encoded-password");
        user.setStatus(0);
        user.setEmailVerified(true);
        return user;
    }
}
