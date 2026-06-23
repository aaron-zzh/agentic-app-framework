package com.xuejiai.aaf.module.system.auth.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.pay.CreditTransactionSourceEnum;
import com.xuejiai.aaf.common.util.NicknameGenerator;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageRequest;
import com.xuejiai.aaf.framework.messaging.MessageSendException;
import com.xuejiai.aaf.framework.messaging.MessageService;
import com.xuejiai.aaf.framework.security.JwtUtils;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.oauth.OAuthClient;
import com.xuejiai.aaf.framework.security.oauth.OAuthUserInfo;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.system.auth.vo.*;
import com.xuejiai.aaf.module.system.role.domain.UserRole;
import com.xuejiai.aaf.module.system.role.repository.RoleRepository;
import com.xuejiai.aaf.module.system.role.repository.UserRoleRepository;
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
    private static final String SMS_VERIFY_CODE_PREFIX = "sms_verify_code:";
    private static final String SMS_VERIFY_CODE_LOCK_PREFIX = "sms_verify_code_lock:";

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
    private final CreditService creditService;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final com.xuejiai.aaf.module.billing.service.SubscriptionService subscriptionService;
    private final com.xuejiai.aaf.module.brokerage.service.BrokerageService brokerageService;
    private final com.xuejiai.aaf.module.system.contact.repository.ContactRepository
            contactRepository;
    private final PhoneRegisterRateLimiter phoneRegisterRateLimiter;

    @Value("${aaf.app.company-name:学记智能}")
    private String companyName;

    @Value("${aaf.messaging.verify-code-channel:EMAIL}")
    private String verifyCodeChannel;

    /** 获取当前登录用户 ID */
    public Long currentUserId() {
        return operatorContext.currentUserId().orElseThrow(() -> exception(AUTH_TOKEN_EXPIRED));
    }

    // ==================== 账号密码登录 ====================

    /** 账号密码登录 */
    public AuthLoginVO login(AuthLoginDTO dto, String deviceId) {
        User user =
                findLoginUser(dto.username())
                        .orElseThrow(() -> exception(AUTH_LOGIN_BAD_CREDENTIALS));
        checkLocked(user);
        if (!user.isActive()) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        if (!user.checkPassword(passwordEncoder, dto.password())) {
            handleLoginFail(user);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw exception(AUTH_EMAIL_NOT_VERIFIED);
        }
        user.recordLoginSuccess(null);
        userRepository.save(user);
        return generateTokensWithSession(user, deviceId);
    }

    private Optional<User> findLoginUser(String account) {
        if (account.contains("@")) {
            return userRepository.findByEmail(account);
        }
        return userRepository.findByUsername(account);
    }

    // ==================== 邮箱注册 ====================

    /** 注册（发送验证码） */
    @Transactional
    public void register(RegisterDTO dto, String sourceApp, String registerIp) {
        if (userRepository.findByEmail(dto.email()).isPresent()) {
            throw exception(AUTH_EMAIL_ALREADY_REGISTERED);
        }
        User user = new User();
        user.setEmail(dto.email());
        user.setUsername(generateUsername(dto.email()));
        user.setNickname(dto.nickname() != null ? dto.nickname() : NicknameGenerator.generate());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setEmailVerified(false);
        user.setSourceApp(sourceApp);
        user.setSourceChannel("local");
        user.setRegisterIp(registerIp);
        user.setRegisterLocation(resolveLocation(registerIp));
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
        assignDefaultRole(user.getId());
        grantRegistrationCredits(user.getId());
        Long contactId = createContactForUser(user);
        bindReferrerIfPresent(contactId, dto.referrerCode());
        brokerageService.tryEnableBrokerage(contactId, "REGISTER");
        notifyDingtalkNewUser(user);
        return generateTokensWithSession(user, deviceId);
    }

    /** 邮箱验证码注册（无需密码，验证通过直接登录） */
    @Transactional
    public AuthLoginVO registerByEmail(
            RegisterByEmailDTO dto, String deviceId, String sourceApp, String registerIp) {
        validateCode(dto.email(), "register", dto.code());
        if (userRepository.findByEmail(dto.email()).isPresent()) {
            throw exception(AUTH_EMAIL_ALREADY_REGISTERED);
        }
        var user = new User();
        user.setEmail(dto.email());
        user.setUsername(generateUsername(dto.email()));
        user.setNickname(dto.nickname() != null ? dto.nickname() : NicknameGenerator.generate());
        user.setPassword(
                passwordEncoder.encode(String.valueOf(ThreadLocalRandom.current().nextLong())));
        user.setEmailVerified(true);
        user.setSourceApp(sourceApp);
        user.setSourceChannel("local");
        user.setRegisterIp(registerIp);
        user.setRegisterLocation(resolveLocation(registerIp));
        userRepository.save(user);
        assignDefaultRole(user.getId());
        grantRegistrationCredits(user.getId());
        Long contactId = createContactForUser(user);
        bindReferrerIfPresent(contactId, dto.referrerCode());
        brokerageService.tryEnableBrokerage(contactId, "REGISTER");
        notifyDingtalkNewUser(user);
        return generateTokensWithSession(user, deviceId);
    }

    // ==================== 验证码 ====================

    /** 发送邮箱验证码 */
    public void sendEmailCode(SendEmailCodeDTO dto) {
        // reset 类型：邮箱必须已注册，否则无法重置
        if ("reset".equals(dto.type()) && !userRepository.existsByEmail(dto.email())) {
            throw exception(AUTH_EMAIL_NOT_REGISTERED);
        }
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
        // 同步发送验证码邮件——失败立即向前端报错，避免用户陷入"发了没收到 + 限频不能重试"死循环
        var templateCode = "auth.verify_code." + dto.type();
        try {
            messageService.sendSync(
                    new MessageRequest(
                            MessageChannel.EMAIL,
                            templateCode,
                            List.of(dto.email()),
                            Map.of(
                                    "code",
                                    code,
                                    "expireMinutes",
                                    systemConfigService.getInteger(
                                            "security.verify_code_expire", 5)),
                            null));
        } catch (MessageSendException e) {
            // 发送失败：释放限频锁 + 清理已写入的验证码，让用户立即重试
            redisTemplate.delete(lockKey);
            redisTemplate.delete(codeKey);
            log.warn(
                    "邮箱验证码发送失败: email={}, type={}, error={}",
                    dto.email(),
                    dto.type(),
                    e.getMessage());
            throw exception(AUTH_VERIFY_CODE_SEND_FAILED);
        }
    }

    /** 发送手机验证码 */
    public void sendSmsCode(SendSmsCodeDTO dto) {
        // 频率限制：同手机号1分钟内不重复发送
        String lockKey = SMS_VERIFY_CODE_LOCK_PREFIX + dto.type() + ":" + dto.phone();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw exception(AUTH_VERIFY_CODE_RATE_LIMIT);
        }
        String code = generateCode();
        String codeKey = SMS_VERIFY_CODE_PREFIX + dto.type() + ":" + dto.phone();
        redisTemplate
                .opsForValue()
                .set(
                        codeKey,
                        code,
                        Duration.ofMinutes(
                                systemConfigService.getInteger("security.verify_code_expire", 5)));
        redisTemplate.opsForValue().set(lockKey, "1", Duration.ofMinutes(1));
        log.info("【手机验证码】手机号={}, 类型={}, 验证码={}", dto.phone(), dto.type(), code);
        // 同步发送短信验证码——SmsChannelSender 内部用 templateCode（register/login/reset）查 sys_sms_template
        // 解析厂商模板
        // 失败立即向前端报错，避免用户陷入"发了没收到 + 限频不能重试"死循环
        try {
            messageService.sendSync(
                    new MessageRequest(
                            MessageChannel.SMS,
                            dto.type(),
                            List.of(dto.phone()),
                            Map.of("code", code),
                            null));
        } catch (MessageSendException e) {
            // 发送失败：释放限频锁 + 清理已写入的验证码，让用户立即重试
            redisTemplate.delete(lockKey);
            redisTemplate.delete(codeKey);
            log.warn(
                    "短信验证码发送失败: phone={}, type={}, error={}",
                    dto.phone(),
                    dto.type(),
                    e.getMessage());
            throw exception(AUTH_VERIFY_CODE_SEND_FAILED);
        }
    }

    // ==================== 验证码登录 ====================

    /** 邮箱+验证码登录（不存在不会自动注册，由前端引导跳到「邮箱验证码注册」流程） */
    public AuthLoginVO loginByEmail(LoginByEmailDTO dto, String deviceId) {
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

    /**
     * 手机号+验证码"登录即注册"。
     *
     * <p>校验短信验证码后，若手机号已存在则走原有登录流程；不存在则先做 IP 风控，通过后自动创建用户、分配默认角色、发放注册积分、建 Contact、激活分销并通知钉钉。
     *
     * @param dto 登录请求（手机号 + 验证码）
     * @param deviceId 设备 ID
     * @param sourceApp 来源 App（web/uniapp/api），仅自动注册分支使用
     * @param registerIp 客户端 IP，用于风控和注册地址解析，仅自动注册分支使用
     * @return 登录结果，{@code isNewUser=true} 表示本次自动创建了用户
     */
    @Transactional
    public AuthLoginVO loginByPhone(
            LoginByPhoneDTO dto, String deviceId, String sourceApp, String registerIp) {
        validateSmsCode(dto.phone(), "login", dto.code());
        var existing = userRepository.findByPhone(dto.phone());
        if (existing.isPresent()) {
            User user = existing.get();
            checkLocked(user);
            if (!user.isActive()) {
                throw exception(AUTH_LOGIN_USER_DISABLED);
            }
            user.recordLoginSuccess(null);
            userRepository.save(user);
            return generateTokensWithSession(user, deviceId, false);
        }
        // 新手机号 → 自动注册分支
        phoneRegisterRateLimiter.checkBeforeRegister(registerIp);
        User user = autoRegisterByPhone(dto.phone(), sourceApp, registerIp, dto.referrerCode());
        phoneRegisterRateLimiter.recordRegister(registerIp);
        return generateTokensWithSession(user, deviceId, true);
    }

    /** 复用原 registerByPhone 副作用顺序：建用户 → 默认角色 → 注册积分 → Contact → 绑推荐人 → 分销 → 钉钉。 */
    private User autoRegisterByPhone(
            String phone, String sourceApp, String registerIp, String referrerCode) {
        var user = new User();
        user.setPhone(phone);
        user.setUsername(generateUsername(phone));
        user.setPassword(
                passwordEncoder.encode(String.valueOf(ThreadLocalRandom.current().nextLong())));
        user.setNickname(NicknameGenerator.generate());
        user.setSourceApp(sourceApp);
        user.setSourceChannel("local");
        user.setRegisterIp(registerIp);
        user.setRegisterLocation(resolveLocation(registerIp));
        userRepository.save(user);
        assignDefaultRole(user.getId());
        grantRegistrationCredits(user.getId());
        Long contactId = createContactForUser(user);
        bindReferrerIfPresent(contactId, referrerCode);
        brokerageService.tryEnableBrokerage(contactId, "REGISTER");
        notifyDingtalkNewUser(user);
        return user;
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

    /** 手机号+验证码 重置密码 */
    @Transactional
    public void resetPasswordByPhone(ResetPasswordByPhoneDTO dto) {
        validateSmsCode(dto.phone(), "reset", dto.code());
        User user =
                userRepository
                        .findByPhone(dto.phone())
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
    public AuthLoginVO oauthLogin(
            String provider,
            String code,
            String deviceId,
            String sourceApp,
            String registerIp,
            String referrerCode) {
        OAuthClient client = findOAuthClient(provider);
        OAuthUserInfo userInfo;
        try {
            userInfo = client.exchangeToken(code);
        } catch (Exception e) {
            log.error("OAuth 授权码换取失败: provider={}, error={}", provider, e.getMessage());
            throw exception(OAUTH_EXCHANGE_FAILED);
        }

        var oauthOpt =
                userOauthRepository.findByProviderAndProviderUserId(
                        userInfo.provider(), userInfo.providerUserId());

        User user;
        if (oauthOpt.isPresent()) {
            user =
                    userRepository
                            .findById(oauthOpt.get().getUserId())
                            .orElseThrow(() -> exception(USER_NOT_FOUND));
            updateOAuthToken(oauthOpt.get(), userInfo);
        } else {
            user = createOAuthUser(userInfo, sourceApp, registerIp, referrerCode);
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

    private User createOAuthUser(
            OAuthUserInfo userInfo, String sourceApp, String registerIp, String referrerCode) {
        var user = new User();
        user.setUsername(userInfo.provider() + "_" + userInfo.providerUserId());
        user.setNickname(
                userInfo.username() != null ? userInfo.username() : NicknameGenerator.generate());
        user.setAvatar(userInfo.avatar());
        user.setPassword(
                passwordEncoder.encode(String.valueOf(ThreadLocalRandom.current().nextLong())));
        user.setEmailVerified(false);
        user.setSourceApp(sourceApp);
        user.setSourceChannel(userInfo.provider());
        user.setRegisterIp(registerIp);
        user.setRegisterLocation(resolveLocation(registerIp));
        userRepository.save(user);
        assignDefaultRole(user.getId());
        grantRegistrationCredits(user.getId());
        // OAuth 注册补齐 contact + 邀请绑定 + 分销资格初始化（与邮箱/手机注册保持对称）
        Long contactId = createContactForUser(user);
        bindReferrerIfPresent(contactId, referrerCode);
        brokerageService.tryEnableBrokerage(contactId, "REGISTER");
        return user;
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
                >= systemConfigService.getInteger("user.login_fail_lock_count", 6)) {
            user.setLockTime(
                    LocalDateTime.now()
                            .plusMinutes(
                                    systemConfigService.getInteger(
                                            "user.login_fail_lock_minutes", 5)));
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

    private void validateSmsCode(String phone, String type, String code) {
        String key = SMS_VERIFY_CODE_PREFIX + type + ":" + phone;
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
            var channel = MessageChannel.valueOf(verifyCodeChannel.toUpperCase());
            // 钉钉渠道收件人用邮箱占位（实际推送到群机器人，recipients 字段忽略）
            var recipients = List.of(email);
            messageService.send(
                    new MessageRequest(
                            channel,
                            "AUTH_VERIFY_CODE",
                            recipients,
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
            // 发送失败不阻断流程，验证码已存 Redis，开发环境可从日志获取
            log.warn("验证码发送失败，邮箱={}, 类型={}, 验证码={}", email, type, code, e);
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
        return generateTokensWithSession(user, deviceId, false);
    }

    private AuthLoginVO generateTokensWithSession(User user, String deviceId, boolean isNewUser) {
        String accessToken = jwtUtils.generateToken(user.getId(), getRoleCodes(user.getId()));
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());
        jwtUtils.saveSession(user.getId(), deviceId, refreshToken);
        return new AuthLoginVO(
                user.getId(),
                accessToken,
                refreshToken,
                jwtUtils.getAccessTokenExpiresTime(),
                isNewUser);
    }

    private List<String> getRoleCodes(Long userId) {
        var roleIds =
                userRoleRepository.findByUserIdAndDeletedFalse(userId).stream()
                        .map(UserRole::getRoleId)
                        .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleRepository.findAllById(roleIds).stream().map(role -> role.getCode()).toList();
    }

    /** 注册赠积分（赠送数量固定 50，不可配置） */
    /** 为新用户分配默认角色（member），角色不存在时静默跳过。 */
    private void assignDefaultRole(Long userId) {
        roleRepository
                .findByCodeAndDeletedFalse("member")
                .ifPresent(
                        role -> {
                            var ur = new UserRole();
                            ur.setUserId(userId);
                            ur.setRoleId(role.getId());
                            userRoleRepository.save(ur);
                        });
    }

    private void grantRegistrationCredits(Long userId) {
        try {
            creditService.earnBatch(
                    userId,
                    50,
                    "MANUAL",
                    CreditTransactionSourceEnum.REGISTER_GIFT.getCode(),
                    String.valueOf(userId),
                    java.time.LocalDateTime.now().plusDays(365));
        } catch (Exception e) {
            log.warn("注册赠积分失败，不影响注册流程: userId={}, err={}", userId, e.getMessage());
        }
        try {
            subscriptionService.subscribe(userId, "FREE", null);
        } catch (Exception e) {
            log.warn("注册分配免费套餐失败，不影响注册流程: userId={}, err={}", userId, e.getMessage());
        }
    }

    /** IP 解析为可读地址，失败返回 null，本地 IP 返回"内网" */
    private String resolveLocation(String ip) {
        if (ip == null || ip.isBlank()) return null;
        if (ip.startsWith("127.")
                || ip.startsWith("192.168.")
                || ip.startsWith("10.")
                || ip.equals("0:0:0:0:0:0:0:1")
                || ip.equals("::1")) {
            return "内网";
        }
        try {
            return com.xuejiai.aaf.common.util.IpUtils.getAreaName(ip);
        } catch (Exception e) {
            log.warn("IP 地址解析失败: {}", ip);
            return null;
        }
    }

    /**
     * 注册时为用户创建 Contact 并关联 user.contact_id。
     *
     * <p>Contact 是分销绑定的主体，注册验证通过后同步创建。 已存在则复用（幂等）。
     */
    private Long createContactForUser(User user) {
        if (user.getContactId() != null) {
            return user.getContactId();
        }
        try {
            var contact = new com.xuejiai.aaf.module.system.contact.domain.Contact();
            contact.setName(user.getNickname() != null ? user.getNickname() : user.getUsername());
            contact.setEmail(user.getEmail());
            contact.setPhone(user.getPhone());
            contact.setType(com.xuejiai.aaf.common.enums.sys.ContactTypeEnum.PERSON);
            contact.setSource(com.xuejiai.aaf.common.enums.sys.ContactSourceEnum.REGISTER);
            contact.setStatus(com.xuejiai.aaf.common.enums.sys.ContactStatusEnum.ACTIVE);
            contactRepository.save(contact);
            user.setContactId(contact.getId());
            userRepository.save(user);
            return contact.getId();
        } catch (Exception e) {
            log.warn("注册创建 Contact 失败，不影响注册流程: userId={}", user.getId(), e);
            return null;
        }
    }

    /** 注册时绑定分销推荐关系（静默失败，不影响注册） */
    private void bindReferrerIfPresent(Long contactId, String referrerCode) {
        if (contactId == null || referrerCode == null || referrerCode.isBlank()) {
            return;
        }
        try {
            brokerageService.bindReferrerByCode(contactId, referrerCode);
        } catch (Exception e) {
            log.warn("绑定推荐人失败，不影响注册流程: contactId={}, code={}", contactId, referrerCode, e);
        }
    }

    /** 新用户注册成功后推送钉钉群通知（静默失败，不影响注册流程） */
    private void notifyDingtalkNewUser(User user) {
        try {
            // 注册方式判断：优先显示手机号（手机注册），否则显示邮箱（邮箱注册）
            String contactLabel;
            String contactValue;
            if (user.getPhone() != null && !user.getPhone().isBlank()) {
                contactLabel = "手机号";
                contactValue = user.getPhone();
            } else {
                contactLabel = "邮箱";
                contactValue = user.getEmail() != null ? user.getEmail() : "-";
            }
            messageService.send(
                    MessageRequest.direct(
                            MessageChannel.DINGTALK,
                            "新用户注册",
                            "**新用户注册** \n\n> 昵称："
                                    + user.getNickname()
                                    + "  \n> "
                                    + contactLabel
                                    + "："
                                    + contactValue
                                    + "  \n> 时间："
                                    + java.time.LocalDateTime.now(),
                            List.of("all")));
        } catch (Exception e) {
            log.warn("钉钉新用户注册通知失败，不影响注册流程: userId={}", user.getId(), e);
        }
    }
}
