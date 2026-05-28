package com.xuejiai.aaf.module.system.user.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.system.user.service.UserService;
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

    private final UserService userService;
    private final ActorContext actorContext;

    @Operation(summary = "获取个人信息")
    @GetMapping
    public Result<UserProfileVO> getProfile() {
        return Result.success(userService.getProfile(currentUserId()));
    }

    @Operation(summary = "修改个人信息")
    @PutMapping
    public Result<UserProfileVO> updateProfile(@Validated @RequestBody UserProfileUpdateDTO req) {
        return Result.success(userService.updateProfile(currentUserId(), req));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Validated @RequestBody UserChangePasswordDTO req) {
        userService.changePassword(currentUserId(), req);
        return Result.success();
    }

    private Long currentUserId() {
        return actorContext
                .currentUserId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
    }
}
