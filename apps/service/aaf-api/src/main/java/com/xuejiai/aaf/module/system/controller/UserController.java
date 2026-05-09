package com.xuejiai.aaf.module.system.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
import com.xuejiai.aaf.module.system.vo.UserVO;

import lombok.RequiredArgsConstructor;

/** 用户管理接口。 */
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/simple")
    public Result<List<UserSimpleVO>> simpleList() {
        return Result.success(userService.getSimpleList());
    }

    @PostMapping
    public Result<UserVO> create(@Validated @RequestBody UserCreateDTO request) {
        return Result.success(userService.create(request));
    }

    @GetMapping("/{id}")
    public Result<UserVO> get(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    @GetMapping
    public Result<PageResult<UserVO>> page(@Validated UserPageDTO request) {
        return Result.success(userService.page(request));
    }

    @PutMapping("/{id}")
    public Result<UserVO> update(
            @PathVariable Long id, @Validated @RequestBody UserUpdateDTO request) {
        return Result.success(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/password")
    public Result<Void> changePassword(
            @PathVariable Long id, @Validated @RequestBody UserChangePasswordDTO request) {
        userService.changePassword(id, request);
        return Result.success();
    }

    @PutMapping("/{id}/password/reset")
    public Result<Void> resetPassword(
            @PathVariable Long id, @Validated @RequestBody UserResetPasswordDTO request) {
        userService.resetPassword(id, request.password());
        return Result.success();
    }
}
