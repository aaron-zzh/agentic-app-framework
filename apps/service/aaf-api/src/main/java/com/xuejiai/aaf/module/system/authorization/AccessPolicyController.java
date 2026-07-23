package com.xuejiai.aaf.module.system.authorization;

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

@Tag(name = "访问策略管理（ABAC）")
@RestController
@RequestMapping("/api/system/access-policies")
@RequiredArgsConstructor
public class AccessPolicyController {

    private final AccessPolicyService service;

    @Operation(summary = "创建策略草稿")
    @PreAuthorize("hasPermission(null, 'system:access-policy:manage')")
    @PostMapping
    public Result<AccessPolicyVO> create(@Validated @RequestBody AccessPolicyCreateDTO dto) {
        return Result.success(service.create(dto));
    }

    @Operation(summary = "更新策略定义")
    @PreAuthorize("hasPermission(null, 'system:access-policy:manage')")
    @PutMapping("/{id}")
    public Result<AccessPolicyVO> update(
            @PathVariable Long id, @Validated @RequestBody AccessPolicyCreateDTO dto) {
        return Result.success(service.update(id, dto));
    }

    @Operation(summary = "删除策略")
    @PreAuthorize("hasPermission(null, 'system:access-policy:manage')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @Operation(summary = "查询全部策略")
    @PreAuthorize("hasPermission(null, 'system:access-policy:manage')")
    @GetMapping
    public Result<List<AccessPolicyVO>> list() {
        return Result.success(service.list());
    }

    @Operation(summary = "发布不可变策略快照")
    @PreAuthorize("hasPermission(null, 'system:access-policy:manage')")
    @PostMapping("/{id}/publish")
    public Result<AccessPolicyVO> publish(
            @PathVariable Long id, @Validated @RequestBody AccessPolicyPublishDTO dto) {
        return Result.success(service.publish(id, dto));
    }

    @Operation(summary = "禁用策略")
    @PreAuthorize("hasPermission(null, 'system:access-policy:manage')")
    @PostMapping("/{id}/disable")
    public Result<AccessPolicyVO> disable(@PathVariable Long id) {
        return Result.success(service.disable(id));
    }

    @Operation(summary = "撤回策略为草稿")
    @PreAuthorize("hasPermission(null, 'system:access-policy:manage')")
    @PostMapping("/{id}/draft")
    public Result<AccessPolicyVO> draft(@PathVariable Long id) {
        return Result.success(service.toDraft(id));
    }

    @Operation(summary = "使用安全 JSON DSL 测试策略")
    @PreAuthorize("hasPermission(null, 'system:access-policy:manage')")
    @PostMapping("/test")
    public Result<PolicyTestResultVO> test(@Validated @RequestBody PolicyTestDTO dto) {
        return Result.success(service.test(dto));
    }
}
