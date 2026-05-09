package com.xuejiai.aaf.module.system.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.service.UserService;
import com.xuejiai.aaf.module.system.vo.UserCreateReqVO;
import com.xuejiai.aaf.module.system.vo.UserRespVO;
import com.xuejiai.aaf.module.system.vo.UserUpdateReqVO;

import lombok.RequiredArgsConstructor;

/** 用户管理接口。 */
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public Result<UserRespVO> create(@Validated @RequestBody UserCreateReqVO request) {
        return Result.success(userService.create(request));
    }

    @GetMapping("/{id}")
    public Result<UserRespVO> get(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    @GetMapping
    public Result<PageResult<UserRespVO>> page(@Validated PageParam param) {
        return Result.success(userService.page(param));
    }

    @PutMapping("/{id}")
    public Result<UserRespVO> update(
            @PathVariable Long id, @Validated @RequestBody UserUpdateReqVO request) {
        return Result.success(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }
}
