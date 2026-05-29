package com.xuejiai.aaf.module.system.auth.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.auth.service.AuthService;
import com.xuejiai.aaf.module.system.auth.vo.*;
import com.xuejiai.aaf.module.system.user.service.UserService;
import com.xuejiai.aaf.module.system.user.vo.UserVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public Result<UserVO> me() {
        Long userId = authService.currentUserId();
        return Result.success(userService.getById(userId));
    }

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public Result<AuthLoginVO> login(
            @Valid @RequestBody AuthLoginDTO dto,
            @RequestHeader(value = "X-Device-Id", defaultValue = "web") String deviceId) {
        return Result.success(authService.login(dto, deviceId));
    }

    @Operation(summary = "邮箱注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return Result.success();
    }

    @Operation(summary = "邮箱验证码注册（无需密码）")
    @PostMapping("/register-by-code")
    public Result<AuthLoginVO> registerByCode(
            @Valid @RequestBody RegisterByCodeDTO dto,
            @RequestHeader(value = "X-Device-Id", defaultValue = "web") String deviceId) {
        return Result.success(authService.registerByCode(dto, deviceId));
    }

    @Operation(summary = "验证邮箱")
    @PostMapping("/verify-email")
    public Result<AuthLoginVO> verifyEmail(
            @Valid @RequestBody VerifyEmailDTO dto,
            @RequestHeader(value = "X-Device-Id", defaultValue = "web") String deviceId) {
        return Result.success(authService.verifyEmail(dto, deviceId));
    }

    @Operation(summary = "发送验证码")
    @PostMapping("/send-code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeDTO dto) {
        authService.sendCode(dto);
        return Result.success();
    }

    @Operation(summary = "邮箱验证码登录")
    @PostMapping("/login-by-code")
    public Result<AuthLoginVO> loginByCode(
            @Valid @RequestBody LoginByCodeDTO dto,
            @RequestHeader(value = "X-Device-Id", defaultValue = "web") String deviceId) {
        return Result.success(authService.loginByCode(dto, deviceId));
    }

    @Operation(summary = "忘记密码")
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto) {
        authService.resetPassword(dto);
        return Result.success();
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public Result<AuthLoginVO> refresh(
            @RequestBody RefreshRequest request,
            @RequestHeader(value = "X-Device-Id", defaultValue = "web") String deviceId) {
        return Result.success(authService.refresh(request.refreshToken(), deviceId));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestBody LogoutRequest request,
            @RequestHeader(value = "X-Device-Id", defaultValue = "web") String deviceId) {
        authService.logout(request.accessToken(), request.refreshToken(), deviceId);
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

    @Operation(summary = "OAuth 回调登录")
    @PostMapping("/oauth/{provider}/callback")
    public Result<AuthLoginVO> oauthCallback(
            @PathVariable String provider, @Valid @RequestBody OAuthCallbackDTO dto) {
        String deviceId = dto.deviceId() != null ? dto.deviceId() : "web";
        return Result.success(authService.oauthLogin(provider, dto.code(), deviceId));
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
}
