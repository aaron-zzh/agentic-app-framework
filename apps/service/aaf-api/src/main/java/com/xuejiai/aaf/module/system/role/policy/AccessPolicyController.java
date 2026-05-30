/**
 * 访问策略管理接口（ABAC）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.system.role.policy;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "访问策略管理（ABAC）")
@RestController
@RequestMapping("/api/system/access-policies")
@RequiredArgsConstructor
public class AccessPolicyController {

    private final AccessPolicyService service;

    @Operation(summary = "创建策略")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public Result<AccessPolicyVO> create(@Validated @RequestBody AccessPolicyCreateDTO dto) {
        return Result.success(service.create(dto));
    }

    @Operation(summary = "更新策略")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Result<AccessPolicyVO> update(@PathVariable Long id, @Validated @RequestBody AccessPolicyCreateDTO dto) {
        return Result.success(service.update(id, dto));
    }

    @Operation(summary = "删除策略")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @Operation(summary = "查询所有启用策略")
    @GetMapping
    public Result<List<AccessPolicyVO>> list() {
        return Result.success(service.listEnabled());
    }

    @Operation(summary = "策略测试")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/test")
    public Result<PolicyTestResultVO> test(@Validated @RequestBody PolicyTestDTO dto) {
        return Result.success(service.test(dto));
    }
}
