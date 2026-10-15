package com.xuejiai.aaf.module.ai.role;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 用户私有 Role 管理接口：仅登录用户维护自己的私有角色。 */
@Tag(name = "我的角色 - 私有 Role")
@RestController
@RequestMapping("/api/ai/my/roles")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MyRoleController {

    private final MyRoleService myRoleService;

    @Operation(summary = "查询我的私有角色")
    @GetMapping
    public Result<List<RoleVO>> list() {
        return Result.success(myRoleService.list());
    }

    @Operation(summary = "创建私有角色时可选的工具")
    @GetMapping("/tool-options")
    public Result<List<RoleReferenceValidator.SelectableTool>> toolOptions() {
        return Result.success(myRoleService.toolOptions());
    }

    @Operation(summary = "查询我的私有角色详情")
    @GetMapping("/{id}")
    public Result<RoleVO> get(@PathVariable Long id) {
        return Result.success(myRoleService.get(id));
    }

    @Operation(summary = "创建私有角色")
    @PostMapping
    public Result<RoleVO> create(@Validated @RequestBody MyRoleSaveDTO request) {
        return Result.success(myRoleService.create(request));
    }

    @Operation(summary = "更新私有角色")
    @PutMapping("/{id}")
    public Result<RoleVO> update(
            @PathVariable Long id, @Validated @RequestBody MyRoleSaveDTO request) {
        return Result.success(myRoleService.update(id, request));
    }

    @Operation(summary = "删除私有角色")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        myRoleService.delete(id);
        return Result.success();
    }
}
