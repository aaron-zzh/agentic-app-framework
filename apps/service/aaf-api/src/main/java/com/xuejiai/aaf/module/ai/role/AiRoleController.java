/**
 * AI Role（能力配置）管理接口。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.role;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "AI 角色管理 - Role")
@RestController
@RequestMapping("/api/ai/roles")
@RequiredArgsConstructor
public class AiRoleController {

    private final AiRoleService aiRoleService;

    @Operation(summary = "Role 列表")
    @GetMapping
    public Result<List<RoleVO>> list() {
        return Result.success(aiRoleService.listRoles());
    }

    @Operation(summary = "Role 详情")
    @GetMapping("/{id}")
    public Result<RoleVO> getById(@PathVariable Long id) {
        return Result.success(aiRoleService.getRoleById(id));
    }

    @Operation(summary = "创建 Role")
    @PostMapping
    public Result<RoleVO> create(@Validated @RequestBody RoleCreateDTO dto) {
        return Result.success(aiRoleService.createRole(dto));
    }

    @Operation(summary = "更新 Role")
    @PutMapping("/{id}")
    public Result<RoleVO> update(@PathVariable Long id, @Validated @RequestBody RoleCreateDTO dto) {
        return Result.success(aiRoleService.updateRole(id, dto));
    }

    @Operation(summary = "删除 Role")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        aiRoleService.deleteRole(id);
        return Result.success();
    }
}
