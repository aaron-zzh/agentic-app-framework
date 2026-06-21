package com.xuejiai.aaf.module.system.user.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.user.service.UserService;
import com.xuejiai.aaf.module.system.user.vo.BindPhoneDTO;
import com.xuejiai.aaf.module.system.user.vo.UserChangePasswordDTO;
import com.xuejiai.aaf.module.system.user.vo.UserProfileUpdateDTO;
import com.xuejiai.aaf.module.system.user.vo.UserProfileVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 用户个人中心接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "用户个人中心")
@RestController
@RequestMapping("/api/system/user/profile")
@RequiredArgsConstructor
public class UserProfileController {

    /** 与 AuthService 保持一致：sms_verify_code:{type}:{phone} */
    private static final String SMS_VERIFY_CODE_PREFIX = "sms_verify_code:";

    private final UserService userService;
    private final OperatorContext operatorContext;
    private final StringRedisTemplate redisTemplate;

    @Operation(summary = "获取个人信息")
    @GetMapping
    public Result<UserProfileVO> getProfile() {
        return Result.success(userService.getProfile(currentUserId()));
    }

    @Operation(summary = "修改个人信息")
    @PreAuthorize("isAuthenticated()")
    @PutMapping
    public Result<UserProfileVO> updateProfile(@Validated @RequestBody UserProfileUpdateDTO req) {
        return Result.success(userService.updateProfile(currentUserId(), req));
    }

    @Operation(summary = "修改密码")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/password")
    public Result<Void> changePassword(@Validated @RequestBody UserChangePasswordDTO req) {
        userService.changePassword(currentUserId(), req);
        return Result.success();
    }

    @Operation(summary = "绑定手机号（短信验证码验证）")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/phone")
    public Result<UserProfileVO> bindPhone(@Validated @RequestBody BindPhoneDTO req) {
        // 校验短信验证码（key 规则与 AuthService 一致）
        validateSmsCode(req.phone(), req.code());
        return Result.success(userService.bindPhone(currentUserId(), req.phone()));
    }

    // ==================== 私有方法 ====================

    private void validateSmsCode(String phone, String code) {
        String key = SMS_VERIFY_CODE_PREFIX + "bind:" + phone;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null || !stored.equals(code)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "验证码错误或已过期");
        }
        redisTemplate.delete(key);
    }

    private Long currentUserId() {
        return operatorContext
                .currentUserId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
    }
}
