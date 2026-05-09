package com.xuejiai.aaf.module.system.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.JwtUtils;

/** 认证接口（v0.1.0 骨架，后续由用户模块替换）。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtils jwtUtils;

    public AuthController(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    /** 临时登录接口，仅用于验证 JWT 流程。 */
    @PostMapping("/login")
    public Result<String> login(@RequestParam(defaultValue = "1") Long userId) {
        String token = jwtUtils.generateToken(userId);
        return Result.success(token);
    }
}
