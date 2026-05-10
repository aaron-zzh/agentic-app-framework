package com.xuejiai.aaf.module.system.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.service.AuthService;
import com.xuejiai.aaf.module.system.service.UserService;
import com.xuejiai.aaf.module.system.vo.AuthLoginDTO;
import com.xuejiai.aaf.module.system.vo.AuthLoginVO;
import com.xuejiai.aaf.module.system.vo.UserVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 认证接口。 */
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
    public Result<AuthLoginVO> login(@Valid @RequestBody AuthLoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public Result<AuthLoginVO> refresh(@RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return Result.success();
    }

    /** 刷新/登出请求体 */
    public record RefreshRequest(String refreshToken) {}
}
