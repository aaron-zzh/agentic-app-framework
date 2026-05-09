package com.xuejiai.aaf.module.system.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.service.AuthService;
import com.xuejiai.aaf.module.system.vo.AuthLoginDTO;
import com.xuejiai.aaf.module.system.vo.AuthLoginVO;

import jakarta.validation.Valid;

/** 认证接口。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 账号密码登录 */
    @PostMapping("/login")
    public Result<AuthLoginVO> login(@Valid @RequestBody AuthLoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    /** 刷新令牌 */
    @PostMapping("/refresh")
    public Result<AuthLoginVO> refresh(@RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request.refreshToken()));
    }

    /** 登出 */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return Result.success();
    }

    /** 刷新/登出请求体 */
    public record RefreshRequest(String refreshToken) {}
}
