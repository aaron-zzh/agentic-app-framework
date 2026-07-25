package com.xuejiai.aaf.module.ai.automation.controller;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.automation.application.DefinitionLifecycleService;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationGovernance.*;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

@RestController
@RequestMapping("/api/ai/definitions")
@PreAuthorize("hasAuthority('ai:definition:manage')")
public class DefinitionLifecycleController {
    private final DefinitionLifecycleService service;
    private final OperatorContext operators;

    public DefinitionLifecycleController(DefinitionLifecycleService service, OperatorContext operators) {
        this.service = service;
        this.operators = operators;
    }

    @PostMapping("/{kind}/{id}/versions/{version}/draft")
    public Result<DefinitionLifecycle> draft(@PathVariable DefinitionKind kind, @PathVariable String id,
            @PathVariable long version, @RequestBody DraftDTO dto) { return Result.success(service.draft(tenant(), kind, id, version, dto.impact())); }
    @PostMapping("/{kind}/{id}/versions/{version}/review")
    public Result<DefinitionLifecycle> review(@PathVariable DefinitionKind kind, @PathVariable String id,
            @PathVariable long version, @RequestBody ReviewDTO dto) { return Result.success(service.review(tenant(), kind, id, version, dto.status(), actor(), dto.reason())); }
    @PostMapping("/{kind}/{id}/versions/{version}/publish")
    public Result<DefinitionLifecycle> publish(@PathVariable DefinitionKind kind, @PathVariable String id, @PathVariable long version) { return Result.success(service.publish(tenant(), kind, id, version)); }
    @PostMapping("/{kind}/{id}/versions/{version}/deprecate")
    public Result<DefinitionLifecycle> deprecate(@PathVariable DefinitionKind kind, @PathVariable String id, @PathVariable long version) { return Result.success(service.deprecate(tenant(), kind, id, version)); }
    @PostMapping("/{kind}/{id}/versions/{version}/disable")
    public Result<DefinitionLifecycle> disable(@PathVariable DefinitionKind kind, @PathVariable String id, @PathVariable long version) { return Result.success(service.disable(tenant(), kind, id, version)); }
    @PostMapping("/{kind}/{id}/versions/{version}/rollback/{targetVersion}")
    public Result<DefinitionLifecycle> rollback(@PathVariable DefinitionKind kind, @PathVariable String id,
            @PathVariable long version, @PathVariable long targetVersion) { return Result.success(service.rollback(tenant(), kind, id, version, targetVersion)); }
    private static TenantId tenant() {
        var orgId = OrgContext.getCurrentOrgId(); if (orgId == null) throw new AccessDeniedException("请求缺少组织上下文");
        return new TenantId(orgId.toString());
    }
    private String actor() {
        return operators.currentOperatorId().map(String::valueOf)
                .orElseThrow(() -> new AccessDeniedException("请求未认证"));
    }

    public record DraftDTO(CompatibilityImpact impact) {}
    public record ReviewDTO(ReviewStatus status, String reason) {}
}
