package com.xuejiai.aaf.module.system.controller;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.service.UserService;
import com.xuejiai.aaf.module.system.vo.UserChangePasswordDTO;
import com.xuejiai.aaf.module.system.vo.UserCreateDTO;
import com.xuejiai.aaf.module.system.vo.UserPageDTO;
import com.xuejiai.aaf.module.system.vo.UserResetPasswordDTO;
import com.xuejiai.aaf.module.system.vo.UserSimpleVO;
import com.xuejiai.aaf.module.system.vo.UserUpdateDTO;
import com.xuejiai.aaf.module.system.vo.UserUpdateStatusDTO;
import com.xuejiai.aaf.module.system.vo.UserVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 用户管理接口。 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取用户精简列表", description = "下拉选择场景，不分页")
    @GetMapping("/simple")
    public Result<List<UserSimpleVO>> simpleList() {
        return Result.success(userService.getSimpleList());
    }

    @Operation(summary = "创建用户")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<UserVO> create(@Validated @RequestBody UserCreateDTO request) {
        return Result.success(userService.create(request));
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public Result<UserVO> get(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    @Operation(summary = "分页查询用户")
    @GetMapping
    public Result<PageResult<UserVO>> page(@Validated @ParameterObject UserPageDTO request) {
        return Result.success(userService.page(request));
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public Result<UserVO> update(
            @PathVariable Long id, @Validated @RequestBody UserUpdateDTO request) {
        return Result.success(userService.update(id, request));
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    @Operation(summary = "批量删除用户")
    @DeleteMapping
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        userService.deleteBatch(ids);
        return Result.success();
    }

    @Operation(summary = "修改用户状态", description = "启用/禁用")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id, @RequestBody UserUpdateStatusDTO request) {
        userService.updateStatus(id, request.status());
        return Result.success();
    }

    @Operation(summary = "修改密码", description = "用户自行修改，需提供旧密码")
    @PutMapping("/{id}/password")
    public Result<Void> changePassword(
            @PathVariable Long id, @Validated @RequestBody UserChangePasswordDTO request) {
        userService.changePassword(id, request);
        return Result.success();
    }

    @Operation(summary = "重置密码", description = "管理员操作，强制重置")
    @PostMapping("/{id}/password/reset")
    public Result<Void> resetPassword(
            @PathVariable Long id, @Validated @RequestBody UserResetPasswordDTO request) {
        userService.resetPassword(id, request.password());
        return Result.success();
    }
}
