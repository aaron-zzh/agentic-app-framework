package com.xuejiai.aaf.module.system.controller;

import java.util.List;

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

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.service.RoleService;
import com.xuejiai.aaf.module.system.vo.RoleCreateDTO;
import com.xuejiai.aaf.module.system.vo.RoleUpdateDTO;
import com.xuejiai.aaf.module.system.vo.RoleVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 角色管理接口。 */
@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "获取角色列表")
    @GetMapping
    public Result<List<RoleVO>> list() {
        return Result.success(roleService.list());
    }

    @Operation(summary = "获取角色详情")
    @GetMapping("/{id}")
    public Result<RoleVO> get(@PathVariable Long id) {
        return Result.success(roleService.getById(id));
    }

    @Operation(summary = "创建角色")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<RoleVO> create(@Validated @RequestBody RoleCreateDTO request) {
        return Result.success(roleService.create(request));
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    public Result<RoleVO> update(@PathVariable Long id, @Validated @RequestBody RoleUpdateDTO request) {
        return Result.success(roleService.update(id, request));
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success();
    }
}
