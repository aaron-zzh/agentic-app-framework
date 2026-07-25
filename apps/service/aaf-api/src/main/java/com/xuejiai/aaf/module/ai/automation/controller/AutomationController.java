package com.xuejiai.aaf.module.ai.automation.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.automation.application.AutomationApplicationService;
import com.xuejiai.aaf.framework.intelligent.automation.application.AutomationApplicationService.DefineCommand;
import com.xuejiai.aaf.framework.intelligent.automation.application.AutomationApplicationService.DryRunResult;
import com.xuejiai.aaf.framework.intelligent.automation.application.AutomationApplicationService.ImpactPreview;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition.FailurePolicy;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition.NotificationPlan;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition.ParameterSchema;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition.TriggerSpec;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationGovernance.OrganizationPolicy;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationRun;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.AuditRecord;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "AI 自动化")
@RestController
@RequestMapping("/api/ai/automations")
@PreAuthorize("isAuthenticated()")
public class AutomationController {
    private final AutomationApplicationService automations;
    private final OperatorContext operators;
    public AutomationController(AutomationApplicationService automations, OperatorContext operators) {
        this.automations = automations; this.operators = operators;
    }

    @GetMapping public Result<List<AutomationDefinition>> list() { return Result.success(automations.list(tenant())); }

    @PostMapping
    public Result<AutomationDefinition> define(@RequestBody DefineAutomationDTO request) {
        return Result.success(automations.define(new DefineCommand(tenant(), request.automationId(), request.name(),
                new TaskId(request.sourceTaskId()), request.parameterSchema(), request.trigger(),
                request.failurePolicy(), request.notificationPlan(), request.policyVersion(), actor())));
    }

    @PostMapping("/{automationId}/versions/{version}/preview")
    public Result<ImpactPreview> preview(@PathVariable String automationId, @PathVariable long version) {
        return Result.success(automations.preview(tenant(), automationId, version, actor()));
    }

    @PostMapping("/{automationId}/versions/{version}/dry-run")
    public Result<DryRunResult> dryRun(@PathVariable String automationId, @PathVariable long version,
            @RequestBody ParametersDTO request) {
        return Result.success(automations.dryRun(tenant(), automationId, version, request.parameters(), actor()));
    }

    @PostMapping("/{automationId}/versions/{version}/publish")
    public Result<AutomationDefinition> publish(@PathVariable String automationId, @PathVariable long version) {
        return Result.success(automations.publish(tenant(), automationId, version, actor()));
    }

    @PostMapping("/{automationId}/enable")
    public Result<AutomationDefinition> enable(@PathVariable String automationId) { return Result.success(automations.enable(tenant(), automationId, actor())); }
    @PostMapping("/{automationId}/disable")
    public Result<AutomationDefinition> disable(@PathVariable String automationId) { return Result.success(automations.disable(tenant(), automationId, actor())); }

    @PostMapping("/{automationId}/trigger")
    public Result<AutomationRun> trigger(@PathVariable String automationId, @RequestBody TriggerDTO request) {
        return Result.success(automations.trigger(tenant(), automationId, request.triggerKey(), request.parameters(), actor()));
    }

    @GetMapping("/{automationId}/history")
    public Result<List<AutomationRun>> history(@PathVariable String automationId) { return Result.success(automations.history(tenant(), automationId)); }

    @GetMapping("/{automationId}/audits")
    public Result<List<AuditRecord>> audits(@PathVariable String automationId,
            @RequestParam Instant from, @RequestParam Instant to) {
        return Result.success(automations.auditHistory(tenant(), automationId, from, to));
    }

    @PostMapping("/global-stop")
    @PreAuthorize("hasAuthority('ai:automation:admin')")
    public Result<OrganizationPolicy> globalStop(@RequestBody GlobalStopDTO request) {
        return Result.success(automations.globalStop(tenant(), request.stopped(), actor()));
    }

    private TenantId tenant() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) throw new AccessDeniedException("请求缺少组织上下文");
        return new TenantId(orgId.toString());
    }
    private String actor() {
        return operators.currentOperatorId().map(String::valueOf).orElseThrow(() -> new AccessDeniedException("请求未认证"));
    }

    public record DefineAutomationDTO(String automationId, String name, String sourceTaskId,
            ParameterSchema parameterSchema, TriggerSpec trigger, FailurePolicy failurePolicy,
            NotificationPlan notificationPlan, String policyVersion) {}
    public record ParametersDTO(Map<String,Object> parameters) {}
    public record TriggerDTO(String triggerKey, Map<String,Object> parameters) {}
    public record GlobalStopDTO(boolean stopped) {}
}
