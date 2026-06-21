package com.xuejiai.aaf.module.system.auth.controller;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.auth.captcha.EsaCaptchaVerifier;
import com.xuejiai.aaf.module.system.auth.service.AuthService;
import com.xuejiai.aaf.module.system.auth.vo.*;
import com.xuejiai.aaf.module.system.role.repository.RoleRepository;
import com.xuejiai.aaf.module.system.role.repository.UserRoleRepository;
import com.xuejiai.aaf.module.system.user.service.UserService;
import com.xuejiai.aaf.module.system.user.vo.UserVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * 认证接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final Environment environment;
    private final EsaCaptchaVerifier esaCaptchaVerifier;

    @Value("${aaf.app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public AuthController(
            AuthService authService,
            UserService userService,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            Environment environment,
            EsaCaptchaVerifier esaCaptchaVerifier) {
        this.authService = authService;
        this.userService = userService;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.environment = environment;
        this.esaCaptchaVerifier = esaCaptchaVerifier;
    }

    /** 当前用户信息（含角色 code 列表） */
    public record MeVO(UserVO user, List<String> roles) {}

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public Result<MeVO> me(
            jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) {
        Long userId = authService.currentUserId();
        var userVO = userService.getById(userId);
        var roleIds =
                userRoleRepository.findByUserIdAndDeletedFalse(userId).stream()
                        .map(ur -> ur.getRoleId())
                        .toList();
        var roles =
                roleIds.isEmpty()
                        ? List.<String>of()
                        : roleRepository.findAllById(roleIds).stream()
                                .map(r -> r.getCode())
                                .toList();
        // 顺带刷新 HttpOnly Cookie，确保切换页面时 SSE 能用 Cookie 认证
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            writeTokenCookie(response, auth.substring(7));
        }
        return Result.success(new MeVO(userVO, roles));
    }

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public Result<AuthLoginVO> login(
            @Valid @RequestBody AuthLoginDTO dto,
            @RequestHeader(value = "X-Device-Id", defaultValue = "web") String deviceId,
            @RequestHeader(value = EsaCaptchaVerifier.HEADER_NAME, required = false)
                    String captchaVerifyParam,
            HttpServletResponse response) {
        esaCaptchaVerifier.verify(captchaVerifyParam, "login");
        var vo = authService.login(dto, deviceId);
        writeTokenCookie(response, vo.accessToken());
        return Result.success(vo);
    }

    @Operation(summary = "邮箱注册")
    @PostMapping("/register")
    public Result<Void> register(
            @Valid @RequestBody RegisterDTO dto,
            @RequestHeader(value = "X-Source-App", defaultValue = "web") String sourceApp,
            @RequestHeader(value = EsaCaptchaVerifier.HEADER_NAME, required = false)
                    String captchaVerifyParam,
            jakarta.servlet.http.HttpServletRequest request) {
        esaCaptchaVerifier.verify(captchaVerifyParam, "register");
        authService.register(dto, sourceApp, getClientIp(request));
        return Result.success();
    }

    @Operation(summary = "邮箱验证码注册（无需密码）")
    @PostMapping("/register-by-code")
    public Result<AuthLoginVO> registerByCode(
            @Valid @RequestBody RegisterByCodeDTO dto,
            @RequestHeader(value = "X-Device-Id", defaultValue = "web") String deviceId,
            @RequestHeader(value = "X-Source-App", defaultValue = "web") String sourceApp,
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response) {
        var vo = authService.registerByCode(dto, deviceId, sourceApp, getClientIp(request));
        writeTokenCookie(response, vo.accessToken());
        return Result.success(vo);
    }

    @Operation(summary = "验证邮箱")
    @PostMapping("/verify-email")
    public Result<AuthLoginVO> verifyEmail(
            @Valid @RequestBody VerifyEmailDTO dto,
            @RequestHeader(value = "X-Device-Id", defaultValue = "web") String deviceId,
            HttpServletResponse response) {
        var vo = authService.verifyEmail(dto, deviceId);
        writeTokenCookie(response, vo.accessToken());
        return Result.success(vo);
    }

    @Operation(summary = "发送验证码")
    @PostMapping("/send-code")
    public Result<Void> sendCode(
            @Valid @RequestBody SendCodeDTO dto,
            @RequestHeader(value = EsaCaptchaVerifier.HEADER_NAME, required = false)
                    String captchaVerifyParam) {
        esaCaptchaVerifier.verify(captchaVerifyParam, "send-code");
        authService.sendCode(dto);
        return Result.success();
    }

    @Operation(summary = "发送手机验证码")
    @PostMapping("/send-sms-code")
    public Result<Void> sendSmsCode(
            @Valid @RequestBody SendSmsCodeDTO dto,
            @RequestHeader(value = EsaCaptchaVerifier.HEADER_NAME, required = false)
                    String captchaVerifyParam) {
        esaCaptchaVerifier.verify(captchaVerifyParam, "send-sms-code");
        authService.sendSmsCode(dto);
        return Result.success();
    }

    @Operation(summary = "手机验证码登录")
    @PostMapping("/login-by-code")
    public Result<AuthLoginVO> loginByCode(
            @Valid @RequestBody LoginByCodeDTO dto,
            @RequestHeader(value = "X-Device-Id", defaultValue = "web") String deviceId,
            HttpServletResponse response) {
        var vo = authService.loginByCode(dto, deviceId);
        writeTokenCookie(response, vo.accessToken());
        return Result.success(vo);
    }

    @Operation(summary = "手机验证码登录（不存在则自动注册）")
    @PostMapping("/login-by-phone")
    public Result<AuthLoginVO> loginByPhone(
            @Valid @RequestBody LoginByPhoneDTO dto,
            @RequestHeader(value = "X-Device-Id", defaultValue = "web") String deviceId,
            @RequestHeader(value = "X-Source-App", defaultValue = "web") String sourceApp,
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response) {
        var vo = authService.loginByPhone(dto, deviceId, sourceApp, getClientIp(request));
        writeTokenCookie(response, vo.accessToken());
        return Result.success(vo);
    }

    @Operation(summary = "忘记密码")
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetPassword(dto);
        return Result.success();
    }

    @Operation(summary = "手机验证码重置密码")
    @PostMapping("/reset-password-by-phone")
    public Result<Void> resetPasswordByPhone(@Valid @RequestBody ResetPasswordByPhoneDTO dto) {
        authService.resetPasswordByPhone(dto);
        return Result.success();
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public Result<AuthLoginVO> refresh(
            @RequestBody RefreshRequest request,
            @RequestHeader(value = "X-Device-Id", defaultValue = "web") String deviceId,
            HttpServletResponse response) {
        var vo = authService.refresh(request.refreshToken(), deviceId);
        writeTokenCookie(response, vo.accessToken());
        return Result.success(vo);
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestBody LogoutRequest request,
            @RequestHeader(value = "X-Device-Id", defaultValue = "web") String deviceId,
            HttpServletResponse response) {
        authService.logout(request.accessToken(), request.refreshToken(), deviceId);
        clearTokenCookie(response);
        return Result.success();
    }

    @Operation(summary = "查看当前用户所有活跃会话")
    @GetMapping("/sessions")
    public Result<List<SessionVO>> sessions() {
        return Result.success(authService.getSessions());
    }

    @Operation(summary = "踢出指定设备")
    @DeleteMapping("/sessions/{deviceId}")
    public Result<Void> kickSession(@PathVariable String deviceId) {
        authService.kickSession(deviceId);
        return Result.success();
    }

    // ==================== OAuth 第三方登录 ====================

    @Operation(summary = "获取 OAuth 授权 URL")
    @GetMapping("/oauth/{provider}/url")
    public Result<String> getOAuthUrl(
            @PathVariable String provider, @RequestParam(defaultValue = "") String state) {
        return Result.success(authService.getOAuthUrl(provider, state));
    }

    @Operation(summary = "OAuth 服务端回调：换 token 后重定向前端")
    @GetMapping("/oauth/{provider}/redirect")
    public ResponseEntity<Void> oauthRedirect(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam(defaultValue = "web") String deviceId,
            jakarta.servlet.http.HttpServletRequest request) {
        AuthLoginVO vo =
                authService.oauthLogin(provider, code, deviceId, "web", getClientIp(request));
        // 重定向到前端登录页，携带 token 参数
        String redirectUrl =
                frontendUrl
                        + "/login?accessToken="
                        + vo.accessToken()
                        + "&refreshToken="
                        + vo.refreshToken();
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    @Operation(summary = "OAuth 回调登录")
    @PostMapping("/oauth/{provider}/callback")
    public Result<AuthLoginVO> oauthCallback(
            @PathVariable String provider,
            @Valid @RequestBody OAuthCallbackDTO dto,
            @RequestHeader(value = "X-Source-App", defaultValue = "web") String sourceApp,
            jakarta.servlet.http.HttpServletRequest request) {
        String deviceId = dto.deviceId() != null ? dto.deviceId() : "web";
        return Result.success(
                authService.oauthLogin(
                        provider, dto.code(), deviceId, sourceApp, getClientIp(request)));
    }

    @Operation(summary = "绑定第三方账号")
    @PostMapping("/oauth/{provider}/bind")
    public Result<Void> bindOAuth(
            @PathVariable String provider, @Valid @RequestBody OAuthBindDTO dto) {
        Long userId = authService.currentUserId();
        authService.bindOAuth(userId, provider, dto.code());
        return Result.success();
    }

    @Operation(summary = "解绑第三方账号")
    @DeleteMapping("/oauth/{provider}/unbind")
    public Result<Void> unbindOAuth(@PathVariable String provider) {
        Long userId = authService.currentUserId();
        authService.unbindOAuth(userId, provider);
        return Result.success();
    }

    /** 刷新请求体 */
    public record RefreshRequest(String refreshToken) {}

    /** 登出请求体 */
    public record LogoutRequest(String accessToken, String refreshToken) {}

    /** 获取客户端真实 IP，优先读 X-Forwarded-For（反向代理场景） */
    private String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        return request.getRemoteAddr();
    }

    /** 写入 HttpOnly aaf-token Cookie，生产加 Secure，开发用 SameSite=Lax 支持 localhost */
    private void writeTokenCookie(HttpServletResponse response, String token) {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        String cookie =
                isProd
                        ? "aaf-token="
                                + token
                                + "; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=604800"
                        : "aaf-token=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=604800";
        response.addHeader("Set-Cookie", cookie);
    }

    /** 登出时清除 aaf-token Cookie */
    private void clearTokenCookie(HttpServletResponse response) {
        response.addHeader(
                "Set-Cookie",
                "aaf-token=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT");
    }
}
