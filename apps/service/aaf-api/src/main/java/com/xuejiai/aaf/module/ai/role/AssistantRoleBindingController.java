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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 角色库与用户自维护的助理角色绑定接口：仅登录用户维护自己的助理。 */
@Tag(name = "角色库与助理角色绑定")
@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AssistantRoleBindingController {

    private final AssistantRoleBindingService bindingService;

    @Operation(summary = "角色库：可绑定的内置、公开与自己的私有角色，标注是否已绑定")
    @GetMapping("/api/ai/roles/library")
    public Result<RoleLibraryVO> library(
            @RequestParam(value = "assistantId", required = false) Long assistantId) {
        return Result.success(bindingService.library(assistantId));
    }

    @Operation(summary = "查询助理已绑定的角色")
    @GetMapping("/api/ai/my/assistants/{assistantId}/roles")
    public Result<List<AssistantRoleBindingVO>> list(@PathVariable Long assistantId) {
        return Result.success(bindingService.list(assistantId));
    }

    @Operation(summary = "绑定角色")
    @PostMapping("/api/ai/my/assistants/{assistantId}/roles")
    public Result<AssistantRoleBindingVO> bind(
            @PathVariable Long assistantId, @Validated @RequestBody BindRoleDTO request) {
        return Result.success(bindingService.bind(assistantId, request));
    }

    @Operation(summary = "调整绑定：排序、启停、设为默认")
    @PutMapping("/api/ai/my/assistants/{assistantId}/roles/{roleId}")
    public Result<AssistantRoleBindingVO> update(
            @PathVariable Long assistantId,
            @PathVariable Long roleId,
            @RequestBody UpdateRoleBindingDTO request) {
        return Result.success(bindingService.update(assistantId, roleId, request));
    }

    @Operation(summary = "解绑角色")
    @DeleteMapping("/api/ai/my/assistants/{assistantId}/roles/{roleId}")
    public Result<Void> unbind(@PathVariable Long assistantId, @PathVariable Long roleId) {
        bindingService.unbind(assistantId, roleId);
        return Result.success();
    }
}
