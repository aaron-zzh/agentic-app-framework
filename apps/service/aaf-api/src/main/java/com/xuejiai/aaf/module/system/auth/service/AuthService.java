package com.xuejiai.aaf.module.system.auth.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageRequest;
import com.xuejiai.aaf.framework.messaging.MessageService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.JwtUtils;
import com.xuejiai.aaf.framework.security.oauth.OAuthClient;
import com.xuejiai.aaf.framework.security.oauth.OAuthUserInfo;
import com.xuejiai.aaf.module.system.auth.vo.*;
import com.xuejiai.aaf.module.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.system.user.domain.User;
import com.xuejiai.aaf.module.system.user.domain.UserOauth;
import com.xuejiai.aaf.module.system.user.repository.UserOauthRepository;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 认证业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String VERIFY_CODE_PREFIX = "verify_code:";
    private static final String VERIFY_CODE_LOCK_PREFIX = "verify_code_lock:";

    private final UserRepository userRepository;
    private final UserOauthRepository userOauthRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final OperatorContext operatorContext;
    private final StringRedisTemplate redisTemplate;
    private final JwtDecoder jwtDecoder;
    private final List<OAuthClient> oauthClients;
    private final MessageService messageService;
    private final SystemConfigService systemConfigService;

    @Value("${aaf.app.company-name:学记智能}")
    private String companyName;

    /** 获取当前登录用户 ID */
    public Long currentUserId() {
        return operatorContext.currentUserId().orElseThrow(() -> exception(AUTH_TOKEN_EXPIRED));
    }

    // ==================== 账号密码登录 ====================

    /** 账号密码登录 */
    public AuthLoginVO login(AuthLoginDTO dto, String deviceId) {
        User user =
                userRepository
                        .findByUsername(dto.username())
                        .orElseThrow(() -> exception(AUTH_LOGIN_BAD_CREDENTIALS));
        checkLocked(user);
        if (!user.isActive()) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        if (!user.checkPassword(passwordEncoder, dto.password())) {
            handleLoginFail(user);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        user.recordLoginSuccess(null);
        userRepository.save(user);
        return generateTokensWithSession(user, deviceId);
    }

    // ==================== 邮箱注册 ====================

    /** 注册（发送验证码） */
    @Transactional
    public void register(RegisterDTO dto) {
        if (userRepository.findByEmail(dto.email()).isPresent()) {
            throw exception(AUTH_EMAIL_ALREADY_REGISTERED);
        }
        // 创建用户（未验证状态）
        User user = new User();
        user.setEmail(dto.email());
        user.setUsername(generateUsername(dto.email()));
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setNickname(dto.nickname() != null ? dto.nickname() : dto.email().split("@")[0]);
        user.setEmailVerified(false);
        userRepository.save(user);
        // 发送验证码
        sendVerifyCode(dto.email(), "register");
    }

    /** 验证邮箱 */
    @Transactional
    public AuthLoginVO verifyEmail(VerifyEmailDTO dto, String deviceId) {
        validateCode(dto.email(), "register", dto.code());
        User user =
                userRepository
                        .findByEmail(dto.email())
                        .orElseThrow(() -> exception(USER_NOT_FOUND));
        user.setEmailVerified(true);
        userRepository.save(user);
        return generateTokensWithSession(user, deviceId);
    }

    /** 邮箱验证码注册（无需密码，验证通过直接登录） */
    @Transactional
    public AuthLoginVO registerByCode(RegisterByCodeDTO dto, String deviceId) {
        validateCode(dto.email(), "register", dto.code());
        if (userRepository.findByEmail(dto.email()).isPresent()) {
            throw exception(AUTH_EMAIL_ALREADY_REGISTERED);
        }
        var user = new User();
        user.setEmail(dto.email());
        user.setUsername(generateUsername(dto.email()));
        user.setPassword(
                passwordEncoder.encode(String.valueOf(ThreadLocalRandom.current().nextLong())));
        user.setNickname(dto.nickname() != null ? dto.nickname() : dto.email().split("@")[0]);
        user.setEmailVerified(true);
        userRepository.save(user);
        return generateTokensWithSession(user, deviceId);
    }

    // ==================== 验证码 ====================

    /** 发送验证码 */
    public void sendCode(SendCodeDTO dto) {
        // 频率限制：同邮箱1分钟内不重复发送
        String lockKey = VERIFY_CODE_LOCK_PREFIX + dto.type() + ":" + dto.email();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw exception(AUTH_VERIFY_CODE_RATE_LIMIT);
        }
        String code = generateCode();
        String codeKey = VERIFY_CODE_PREFIX + dto.type() + ":" + dto.email();
        redisTemplate
                .opsForValue()
                .set(
                        codeKey,
                        code,
                        Duration.ofMinutes(
                                systemConfigService.getInteger("security.verify_code_expire", 5)));
        redisTemplate.opsForValue().set(lockKey, "1", Duration.ofMinutes(1));
        log.info("【验证码】邮箱={}, 类型={}, 验证码={}", dto.email(), dto.type(), code);
        // 发送验证码邮件
        var templateCode = "auth.verify_code." + dto.type();
        messageService.send(
                new MessageRequest(
                        MessageChannel.EMAIL,
                        templateCode,
                        List.of(dto.email()),
                        Map.of(
                                "code",
                                code,
                                "expireMinutes",
                                systemConfigService.getInteger("security.verify_code_expire", 5)),
                        null));
    }

    // ==================== 验证码登录 ====================

    /** 邮箱+验证码登录 */
    public AuthLoginVO loginByCode(LoginByCodeDTO dto, String deviceId) {
        validateCode(dto.email(), "login", dto.code());
        User user =
                userRepository
                        .findByEmail(dto.email())
                        .orElseThrow(() -> exception(AUTH_LOGIN_BAD_CREDENTIALS));
        checkLocked(user);
        if (!user.isActive()) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        user.recordLoginSuccess(null);
        userRepository.save(user);
        return generateTokensWithSession(user, deviceId);
    }

    // ==================== 忘记密码 ====================

    /** 重置密码 */
    @Transactional
    public void resetPassword(ResetPasswordDTO dto) {
        validateCode(dto.email(), "reset", dto.code());
        User user =
                userRepository
                        .findByEmail(dto.email())
                        .orElseThrow(() -> exception(USER_NOT_FOUND));
        user.changePassword(passwordEncoder, dto.newPassword());
        userRepository.save(user);
    }

    // ==================== 刷新/登出 ====================

    /** 刷新令牌 */
    public AuthLoginVO refresh(String refreshToken, String deviceId) {
        Long userId = jwtUtils.validateRefreshToken(refreshToken);
        if (userId == null) {
            throw exception(AUTH_TOKEN_EXPIRED);
        }
        User user =
                userRepository.findById(userId).orElseThrow(() -> exception(AUTH_TOKEN_EXPIRED));
        if (!user.isActive()) {
            jwtUtils.revokeRefreshToken(refreshToken);
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        jwtUtils.revokeRefreshToken(refreshToken);
        return generateTokensWithSession(user, deviceId);
    }

    /** 登出（黑名单 accessToken + 删除当前设备会话） */
    public void logout(String accessToken, String refreshToken, String deviceId) {
        // accessToken 加入黑名单
        try {
            Jwt jwt = jwtDecoder.decode(accessToken);
            if (jwt.getId() != null && jwt.getExpiresAt() != null) {
                jwtUtils.blacklistToken(jwt.getId(), jwt.getExpiresAt());
            }
        } catch (Exception ignored) {
            // token 已过期或无效，忽略
        }
        // 删除 refreshToken
        jwtUtils.revokeRefreshToken(refreshToken);
        // 删除设备会话
        Long userId = operatorContext.currentUserId().orElse(null);
        if (userId != null) {
            jwtUtils.removeSession(userId, deviceId);
        }
    }

    // ==================== 会话管理 ====================

    /** 获取当前用户所有活跃会话 */
    public List<SessionVO> getSessions() {
        Long userId = currentUserId();
        Map<Object, Object> sessions = jwtUtils.getSessions(userId);
        return sessions.entrySet().stream()
                .map(
                        e -> {
                            String value = (String) e.getValue();
                            String loginTime = value.contains("|") ? value.split("\\|")[1] : "";
                            return new SessionVO((String) e.getKey(), loginTime);
                        })
                .toList();
    }

    /** 踢出指定设备 */
    public void kickSession(String deviceId) {
        Long userId = currentUserId();
        jwtUtils.removeSession(userId, deviceId);
    }

    // ==================== OAuth 第三方登录 ====================

    /** 获取 OAuth 授权 URL */
    public String getOAuthUrl(String provider, String state) {
        OAuthClient client = findOAuthClient(provider);
        return client.buildAuthorizationUrl(state);
    }

    /** OAuth 回调登录 */
    @Transactional
    public AuthLoginVO oauthLogin(String provider, String code, String deviceId) {
        OAuthClient client = findOAuthClient(provider);
        OAuthUserInfo userInfo;
        try {
            userInfo = client.exchangeToken(code);
        } catch (Exception e) {
            log.error("OAuth 授权码换取失败: provider={}, error={}", provider, e.getMessage());
            throw exception(OAUTH_EXCHANGE_FAILED);
        }

        // 查找是否已绑定
        var oauthOpt =
                userOauthRepository.findByProviderAndProviderUserId(
                        userInfo.provider(), userInfo.providerUserId());

        User user;
        if (oauthOpt.isPresent()) {
            // 已绑定，直接登录
            user =
                    userRepository
                            .findById(oauthOpt.get().getUserId())
                            .orElseThrow(() -> exception(USER_NOT_FOUND));
            // 更新 token
            updateOAuthToken(oauthOpt.get(), userInfo);
        } else {
            // 未绑定，自动创建用户并绑定
            user = createOAuthUser(userInfo);
            createOAuthBinding(user.getId(), userInfo);
        }

        user.recordLoginSuccess(null);
        userRepository.save(user);
        return generateTokensWithSession(user, deviceId);
    }

    /** 已登录用户绑定第三方账号 */
    @Transactional
    public void bindOAuth(Long userId, String provider, String code) {
        OAuthClient client = findOAuthClient(provider);
        OAuthUserInfo userInfo;
        try {
            userInfo = client.exchangeToken(code);
        } catch (Exception e) {
            log.error("OAuth 绑定换取失败: provider={}, error={}", provider, e.getMessage());
            throw exception(OAUTH_EXCHANGE_FAILED);
        }

        // 检查是否已被其他用户绑定
        var existing =
                userOauthRepository.findByProviderAndProviderUserId(
                        userInfo.provider(), userInfo.providerUserId());
        if (existing.isPresent()) {
            throw exception(OAUTH_ALREADY_BOUND);
        }

        createOAuthBinding(userId, userInfo);
    }

    /** 解绑第三方账号 */
    @Transactional
    public void unbindOAuth(Long userId, String provider) {
        var oauthList = userOauthRepository.findByUserId(userId);
        var target =
                oauthList.stream()
                        .filter(o -> o.getProvider().equals(provider))
                        .findFirst()
                        .orElseThrow(() -> exception(OAUTH_NOT_BOUND));
        userOauthRepository.delete(target);
    }

    private OAuthClient findOAuthClient(String provider) {
        return oauthClients.stream()
                .filter(c -> c.provider().equals(provider))
                .findFirst()
                .orElseThrow(() -> exception(OAUTH_PROVIDER_NOT_CONFIGURED));
    }

    private User createOAuthUser(OAuthUserInfo userInfo) {
        var user = new User();
        user.setUsername(userInfo.provider() + "_" + userInfo.providerUserId());
        user.setNickname(userInfo.username() != null ? userInfo.username() : user.getUsername());
        user.setAvatar(userInfo.avatar());
        user.setPassword(
                passwordEncoder.encode(String.valueOf(ThreadLocalRandom.current().nextLong())));
        user.setEmailVerified(false);
        return userRepository.save(user);
    }

    private void createOAuthBinding(Long userId, OAuthUserInfo userInfo) {
        var oauth = new UserOauth();
        oauth.setUserId(userId);
        oauth.setProvider(userInfo.provider());
        oauth.setProviderUserId(userInfo.providerUserId());
        oauth.setProviderUsername(userInfo.username());
        oauth.setAccessToken(userInfo.accessToken());
        oauth.setRefreshToken(userInfo.refreshToken());
        if (userInfo.expiresIn() > 0) {
            oauth.setTokenExpireTime(LocalDateTime.now().plusSeconds(userInfo.expiresIn()));
        }
        userOauthRepository.save(oauth);
    }

    private void updateOAuthToken(UserOauth oauth, OAuthUserInfo userInfo) {
        oauth.setAccessToken(userInfo.accessToken());
        oauth.setRefreshToken(userInfo.refreshToken());
        oauth.setProviderUsername(userInfo.username());
        if (userInfo.expiresIn() > 0) {
            oauth.setTokenExpireTime(LocalDateTime.now().plusSeconds(userInfo.expiresIn()));
        }
        userOauthRepository.save(oauth);
    }

    // ==================== 私有方法 ====================

    private void checkLocked(User user) {
        if (user.isLocked()) {
            throw exception(AUTH_USER_LOCKED);
        }
    }

    private void handleLoginFail(User user) {
        user.recordLoginFail();
        if (user.getLoginFailCount()
                >= systemConfigService.getInteger("user.login_fail_lock_count", 5)) {
            user.setLockTime(
                    LocalDateTime.now()
                            .plusMinutes(
                                    systemConfigService.getInteger(
                                            "user.login_fail_lock_minutes", 30)));
        }
        userRepository.save(user);
    }

    private void validateCode(String email, String type, String code) {
        String key = VERIFY_CODE_PREFIX + type + ":" + email;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null || !stored.equals(code)) {
            throw exception(AUTH_VERIFY_CODE_INVALID);
        }
        redisTemplate.delete(key);
    }

    private void sendVerifyCode(String email, String type) {
        String code = generateCode();
        String codeKey = VERIFY_CODE_PREFIX + type + ":" + email;
        redisTemplate
                .opsForValue()
                .set(
                        codeKey,
                        code,
                        Duration.ofMinutes(
                                systemConfigService.getInteger("security.verify_code_expire", 5)));
        try {
            messageService.send(
                    new MessageRequest(
                            MessageChannel.EMAIL,
                            "AUTH_VERIFY_CODE",
                            List.of(email),
                            Map.of(
                                    "code",
                                    code,
                                    "type",
                                    type,
                                    "expireMinutes",
                                    systemConfigService.getInteger(
                                            "security.verify_code_expire", 5),
                                    "companyName",
                                    companyName),
                            "【" + companyName + "】安全验证码"));
        } catch (Exception e) {
            // 邮件发送失败不阻断流程，验证码已存 Redis，开发环境可从日志获取
            log.warn("验证码邮件发送失败，邮箱={}, 类型={}, 验证码={}", email, type, code, e);
        }
    }

    private String generateCode() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));
    }

    private String generateUsername(String email) {
        String prefix = email.split("@")[0];
        String suffix = String.valueOf(ThreadLocalRandom.current().nextInt(1000, 9999));
        return prefix + suffix;
    }

    private AuthLoginVO generateTokensWithSession(User user, String deviceId) {
        String accessToken = jwtUtils.generateToken(user.getId());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());
        jwtUtils.saveSession(user.getId(), deviceId, refreshToken);
        return new AuthLoginVO(
                user.getId(), accessToken, refreshToken, jwtUtils.getAccessTokenExpiresTime());
    }
}
