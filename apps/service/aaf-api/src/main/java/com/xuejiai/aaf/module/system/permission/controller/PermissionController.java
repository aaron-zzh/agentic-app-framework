package com.xuejiai.aaf.module.system.permission.controller;

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
import com.xuejiai.aaf.module.system.permission.service.PermissionService;
import com.xuejiai.aaf.module.system.permission.vo.PermissionCreateDTO;
import com.xuejiai.aaf.module.system.permission.vo.PermissionTreeVO;
import com.xuejiai.aaf.module.system.permission.vo.PermissionUpdateDTO;
import com.xuejiai.aaf.module.system.permission.vo.PermissionVO;
import com.xuejiai.aaf.module.system.role.vo.RoleVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 权限点管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "权限点管理")
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "获取权限点树形列表")
    @GetMapping("/permissions")
    public Result<List<PermissionTreeVO>> tree() {
        return Result.success(permissionService.tree());
    }

    @Operation(summary = "创建权限点")
    @PostMapping("/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<PermissionVO> create(@Validated @RequestBody PermissionCreateDTO request) {
        return Result.success(permissionService.create(request));
    }

    @Operation(summary = "更新权限点")
    @PutMapping("/permissions/{id}")
    public Result<PermissionVO> update(
            @PathVariable Long id, @Validated @RequestBody PermissionUpdateDTO request) {
        return Result.success(permissionService.update(id, request));
    }

    @Operation(summary = "删除权限点")
    @DeleteMapping("/permissions/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return Result.success();
    }

    @Operation(summary = "为角色分配权限点")
    @PostMapping("/roles/{roleId}/permissions")
    public Result<Void> assignPermissionsToRole(
            @PathVariable Long roleId, @RequestBody List<Long> permissionIds) {
        permissionService.assignPermissionsToRole(roleId, permissionIds);
        return Result.success();
    }

    @Operation(summary = "查询角色拥有的权限点")
    @GetMapping("/roles/{roleId}/permissions")
    public Result<List<PermissionVO>> getPermissionsByRole(@PathVariable Long roleId) {
        return Result.success(permissionService.getPermissionsByRoleId(roleId));
    }

    @Operation(summary = "为用户分配角色")
    @PostMapping("/users/{userId}/roles")
    public Result<Void> assignRolesToUser(
            @PathVariable Long userId, @RequestBody List<Long> roleIds) {
        permissionService.assignRolesToUser(userId, roleIds);
        return Result.success();
    }

    @Operation(summary = "查询用户拥有的角色")
    @GetMapping("/users/{userId}/roles")
    public Result<List<RoleVO>> getRolesByUser(@PathVariable Long userId) {
        return Result.success(permissionService.getRolesByUserId(userId));
    }

    @Operation(summary = "移除用户的某个角色")
    @DeleteMapping("/users/{userId}/roles/{roleId}")
    public Result<Void> removeRoleFromUser(@PathVariable Long userId, @PathVariable Long roleId) {
        permissionService.removeRoleFromUser(userId, roleId);
        return Result.success();
    }
}
