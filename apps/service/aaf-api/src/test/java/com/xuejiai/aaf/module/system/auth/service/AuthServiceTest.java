package com.xuejiai.aaf.module.system.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageRequest;
import com.xuejiai.aaf.framework.messaging.MessageService;
import com.xuejiai.aaf.framework.security.JwtUtils;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.billing.service.SubscriptionService;
import com.xuejiai.aaf.module.brokerage.service.BrokerageService;
import com.xuejiai.aaf.module.system.ErrorCodeConstants;
import com.xuejiai.aaf.module.system.auth.vo.AuthLoginDTO;
import com.xuejiai.aaf.module.system.auth.vo.LoginByPhoneDTO;
import com.xuejiai.aaf.module.system.auth.vo.SendEmailCodeDTO;
import com.xuejiai.aaf.module.system.contact.repository.ContactRepository;
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
    @Mock private CreditService creditService;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private BrokerageService brokerageService;
    @Mock private ContactRepository contactRepository;
    @Mock private PhoneRegisterRateLimiter phoneRegisterRateLimiter;

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
        assertThat(result.isNewUser()).isFalse();
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
        assertThat(result.isNewUser()).isFalse();
        verify(userRepository).findByEmail("test@example.com");
        verify(userRepository, never()).findByUsername(anyString());
        verify(userRepository).save(user);
        verify(jwtUtils).saveSession(1L, "device-1", "refresh-token");
    }

    @Test
    @DisplayName("Given 有效邮箱 When sendEmailCode Then 验证码存入 Redis 并发送邮件")
    void should_store_code_in_redis_and_send_email_when_send_code() {
        stubVerifyCodeConfig();
        // 准备参数
        var dto = new SendEmailCodeDTO("test@example.com", "register");

        // 调用
        authService.sendEmailCode(dto);

        // 断言：验证码存入 Redis（5分钟）
        verify(valueOps)
                .set(
                        eq("verify_code:register:test@example.com"),
                        anyString(),
                        eq(java.time.Duration.ofMinutes(5)));

        // 断言：邮件通过 MessageService 同步发送
        var captor = ArgumentCaptor.forClass(MessageRequest.class);
        verify(messageService).sendSync(captor.capture());
        var request = captor.getValue();
        assertThat(request.channel()).isEqualTo(MessageChannel.EMAIL);
        assertThat(request.templateCode()).isEqualTo("auth.verify_code.register");
        assertThat(request.recipients()).containsExactly("test@example.com");
        assertThat(request.variables()).containsEntry("expireMinutes", 5);
    }

    @Test
    @DisplayName("Given MessageService 发送失败 When sendEmailCode Then 抛 AUTH_VERIFY_CODE_SEND_FAILED 并释放限频锁")
    void should_throw_business_error_and_release_lock_when_email_send_fails() {
        stubVerifyCodeConfig();
        // 准备参数
        var dto = new SendEmailCodeDTO("test@example.com", "login");

        // mock 邮件同步发送失败
        org.mockito.Mockito.doThrow(
                        new com.xuejiai.aaf.framework.messaging.MessageSendException(
                                "SMTP 连接失败", MessageChannel.EMAIL, new RuntimeException()))
                .when(messageService)
                .sendSync(any());

        // 调用 + 断言：抛出 AUTH_VERIFY_CODE_SEND_FAILED 业务异常（前端可识别错误码并提示）
        assertThatThrownBy(() -> authService.sendEmailCode(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.AUTH_VERIFY_CODE_SEND_FAILED.code());

        // 断言：限频锁与验证码均已释放，让用户立即重试
        verify(redisTemplate).delete("verify_code_lock:login:test@example.com");
        verify(redisTemplate).delete("verify_code:login:test@example.com");
    }

    // ==================== loginByPhone：登录即注册 ====================

    @Test
    @DisplayName("Given 老手机号 When loginByPhone Then 走原有登录流程，isNewUser=false")
    void should_login_existing_user_when_phone_already_registered() {
        // 准备参数
        var phone = "13800138000";
        var user = phoneUser(phone);
        stubSmsCodeValid(phone);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));
        stubTokenGeneration();

        // 调用
        var result =
                authService.loginByPhone(
                        new LoginByPhoneDTO(phone, "123456"), "device-1", "web", "1.2.3.4");

        // 断言
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.isNewUser()).isFalse();
        verify(userRepository).save(user);
        // 老用户登录不触发 IP 风控（用例 #6 同等覆盖，单独再断言一次保证语义清晰）
        verify(phoneRegisterRateLimiter, never()).checkBeforeRegister(anyString());
        verify(phoneRegisterRateLimiter, never()).recordRegister(anyString());
        // 不应触发自动注册副作用
        verify(creditService, never()).earn(any(), org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString());
        verify(brokerageService, never()).tryEnableBrokerage(any(), anyString());
    }

    @Test
    @DisplayName("Given 新手机号 When loginByPhone Then 自动注册并执行完整副作用，isNewUser=true")
    void should_auto_register_when_phone_not_exists() {
        // 准备参数
        var phone = "13800138001";
        stubSmsCodeValid(phone);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random");
        when(userRepository.save(any(User.class)))
                .thenAnswer(
                        inv -> {
                            User u = inv.getArgument(0);
                            if (u.getId() == null) u.setId(99L);
                            return u;
                        });
        stubDefaultRole();
        stubContactSave();
        stubTokenGeneration(99L);

        // 调用
        var result =
                authService.loginByPhone(
                        new LoginByPhoneDTO(phone, "123456"), "device-1", "uniapp", "1.2.3.4");

        // 断言：返回标记为新用户
        assertThat(result.isNewUser()).isTrue();
        assertThat(result.accessToken()).isEqualTo("access-token");

        // 断言：用户被创建并持久化（手机号、来源 App、注册 IP 正确）
        var userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(userCaptor.capture());
        var savedUser = userCaptor.getAllValues().get(0);
        assertThat(savedUser.getPhone()).isEqualTo(phone);
        assertThat(savedUser.getSourceApp()).isEqualTo("uniapp");
        assertThat(savedUser.getSourceChannel()).isEqualTo("local");
        assertThat(savedUser.getRegisterIp()).isEqualTo("1.2.3.4");
        assertThat(savedUser.getNickname()).isNotBlank();

        // 断言：副作用全部执行
        verify(userRoleRepository).save(any()); // 默认角色分配
        verify(creditService).earn(eq(99L), eq(50L), anyString(), anyString()); // 注册积分
        verify(contactRepository).save(any()); // Contact 创建
        verify(brokerageService).tryEnableBrokerage(any(), eq("REGISTER")); // 分销激活
        verify(messageService).send(any(MessageRequest.class)); // 钉钉通知

        // 断言：风控 check 在前，record 在后
        var inOrder = org.mockito.Mockito.inOrder(phoneRegisterRateLimiter, userRepository);
        inOrder.verify(phoneRegisterRateLimiter).checkBeforeRegister("1.2.3.4");
        inOrder.verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(any(User.class));
        inOrder.verify(phoneRegisterRateLimiter).recordRegister("1.2.3.4");
    }

    @Test
    @DisplayName("Given 验证码错误 When loginByPhone Then 抛出 AUTH_VERIFY_CODE_INVALID")
    void should_throw_when_sms_code_invalid() {
        // 准备参数
        var phone = "13800138002";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("sms_verify_code:login:" + phone)).thenReturn("999999");

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                authService.loginByPhone(
                                        new LoginByPhoneDTO(phone, "123456"),
                                        "device-1",
                                        "web",
                                        "1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.AUTH_VERIFY_CODE_INVALID.code());

        // 不应进入用户查询/风控/注册
        verify(userRepository, never()).findByPhone(anyString());
        verify(phoneRegisterRateLimiter, never()).checkBeforeRegister(anyString());
    }

    @Test
    @DisplayName("Given 同 IP 1 小时已注册 10 次 When loginByPhone 自动注册 Then 抛出 AUTH_REGISTER_IP_RATE_LIMIT")
    void should_throw_when_ip_register_limit_exceeded() {
        // 准备参数
        var phone = "13800138003";
        var ip = "9.9.9.9";
        stubSmsCodeValid(phone);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.empty());
        // mock 风控触发限流
        org.mockito.Mockito.doThrow(
                        com.xuejiai.aaf.common.exception.ExceptionUtil.exception(
                                ErrorCodeConstants.AUTH_REGISTER_IP_RATE_LIMIT))
                .when(phoneRegisterRateLimiter)
                .checkBeforeRegister(ip);

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                authService.loginByPhone(
                                        new LoginByPhoneDTO(phone, "123456"),
                                        "device-1",
                                        "web",
                                        ip))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.AUTH_REGISTER_IP_RATE_LIMIT.code());

        // 风控触发后，不应进入注册副作用
        verify(userRepository, never()).save(any(User.class));
        verify(phoneRegisterRateLimiter, never()).recordRegister(anyString());
        verify(creditService, never()).earn(any(), org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Given 老用户手机登录 When loginByPhone Then 不触发 IP 风控计数器")
    void should_not_count_ip_for_existing_user_login() {
        // 准备参数
        var phone = "13800138004";
        var user = phoneUser(phone);
        stubSmsCodeValid(phone);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));
        stubTokenGeneration();

        // 调用
        authService.loginByPhone(
                new LoginByPhoneDTO(phone, "123456"), "device-1", "web", "1.2.3.4");

        // 断言：风控不被调用
        verify(phoneRegisterRateLimiter, never()).checkBeforeRegister(anyString());
        verify(phoneRegisterRateLimiter, never()).recordRegister(anyString());
    }

    @Test
    @DisplayName("Given 锁定用户 When loginByPhone Then 抛出 AUTH_USER_LOCKED")
    void should_throw_when_user_locked() {
        // 准备参数
        var phone = "13800138005";
        var user = phoneUser(phone);
        user.setLockTime(LocalDateTime.now().plusMinutes(5));
        stubSmsCodeValid(phone);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                authService.loginByPhone(
                                        new LoginByPhoneDTO(phone, "123456"),
                                        "device-1",
                                        "web",
                                        "1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.AUTH_USER_LOCKED.code());
    }

    @Test
    @DisplayName("Given 禁用用户 When loginByPhone Then 抛出 AUTH_LOGIN_USER_DISABLED")
    void should_throw_when_user_disabled() {
        // 准备参数
        var phone = "13800138006";
        var user = phoneUser(phone);
        user.setStatus(1); // 禁用
        stubSmsCodeValid(phone);
        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                authService.loginByPhone(
                                        new LoginByPhoneDTO(phone, "123456"),
                                        "device-1",
                                        "web",
                                        "1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED.code());
    }

    // ==================== helpers ====================

    private void stubSmsCodeValid(String phone) {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("sms_verify_code:login:" + phone)).thenReturn("123456");
    }

    private void stubDefaultRole() {
        var role = new com.xuejiai.aaf.module.system.role.domain.Role();
        role.setId(10L);
        role.setCode("member");
        when(roleRepository.findByCodeAndDeletedFalse("member")).thenReturn(Optional.of(role));
    }

    private void stubContactSave() {
        when(contactRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            var c = inv.getArgument(0);
                            org.springframework.test.util.ReflectionTestUtils.setField(
                                    c, "id", 200L);
                            return c;
                        });
    }

    private void stubTokenGeneration() {
        stubTokenGeneration(1L);
    }

    private void stubTokenGeneration(long userId) {
        when(jwtUtils.generateToken(eq(userId), any())).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(userId)).thenReturn("refresh-token");
        when(jwtUtils.getAccessTokenExpiresTime()).thenReturn(LocalDateTime.now().plusHours(1));
        when(userRoleRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(List.of());
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

    private User phoneUser(String phone) {
        var user = new User();
        user.setId(1L);
        user.setUsername("phoneuser");
        user.setPhone(phone);
        user.setPassword("encoded-password");
        user.setStatus(0);
        return user;
    }
}
