package com.xuejiai.aaf.framework.intelligent.automation.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskBoard;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskBoardPort;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition.FailurePolicy;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition.NotificationPlan;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition.ParameterSchema;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition.PermissionSnapshot;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition.TaskTemplate;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationDefinition.TriggerSpec;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationGovernance.OrganizationPolicy;
import com.xuejiai.aaf.framework.intelligent.automation.model.AutomationRun;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.AuditPort;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.AuditRecord;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.DefinitionPort;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.DispatchPort;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.PolicyPort;
import com.xuejiai.aaf.framework.intelligent.automation.port.AutomationPorts.RunPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 非 Team 自动化应用服务；长期调度只经确定性 DispatchPort。 */
public final class AutomationApplicationService {
    private final DefinitionPort definitions;
    private final RunPort runs;
    private final PolicyPort policies;
    private final AuditPort audits;
    private final DispatchPort dispatcher;
    private final DelegatedTaskPort delegatedTasks;
    private final TaskBoardPort taskBoards;
    private final Clock clock;

    public AutomationApplicationService(
            DefinitionPort definitions,
            RunPort runs,
            PolicyPort policies,
            AuditPort audits,
            DispatchPort dispatcher,
            DelegatedTaskPort delegatedTasks,
            TaskBoardPort taskBoards,
            Clock clock) {
        this.definitions = Objects.requireNonNull(definitions);
        this.runs = Objects.requireNonNull(runs);
        this.policies = Objects.requireNonNull(policies);
        this.audits = Objects.requireNonNull(audits);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.delegatedTasks = Objects.requireNonNull(delegatedTasks);
        this.taskBoards = Objects.requireNonNull(taskBoards);
        this.clock = Objects.requireNonNull(clock);
    }

    public AutomationDefinition define(DefineCommand request) {
        var stored =
                delegatedTasks
                        .find(request.tenantId(), request.sourceTaskId())
                        .orElseThrow(() -> new IllegalArgumentException("P4 委托任务不存在"));
        var source = stored.task();
        var command = stored.command();
        if (source.status() != Status.COMPLETED
                || command.controlMode() != ControlMode.DELEGATED
                || command.executionContract() == null) {
            throw new IllegalStateException("只有完成、可委托、合同完整且无未决授权的 P4 任务可保存模板");
        }
        var board =
                taskBoards
                        .find(request.tenantId(), request.sourceTaskId())
                        .orElseThrow(() -> new IllegalStateException("P4 任务缺少验收完成的 TaskBoard"));
        if (!board.completed()) throw new IllegalStateException("P4 TaskBoard 尚未完成验收");
        var now = clock.instant();
        var previous = definitions.findLatest(request.tenantId(), request.automationId());
        var version = previous.map(value -> value.version() + 1).orElse(1L);
        var window = Duration.between(source.createdAt(), source.contract().deadline());
        if (window.isZero() || window.isNegative()) throw new IllegalStateException("源任务执行窗口无效");
        var template =
                new TaskTemplate(
                        source.userId(),
                        command.assistantId(),
                        command.memorySubject(),
                        command.input(),
                        command.completionCriteria(),
                        command.contextCandidates(),
                        resetBoard(board, request.sourceTaskId()),
                        source.contract(),
                        window);
        var definition =
                new AutomationDefinition(
                        request.tenantId(),
                        request.automationId(),
                        request.name(),
                        version,
                        request.sourceTaskId(),
                        template,
                        request.parameterSchema(),
                        request.trigger(),
                        new PermissionSnapshot(
                                source.contract().allowedActions(), request.policyVersion(), now),
                        request.failurePolicy(),
                        request.notificationPlan(),
                        AutomationDefinition.Lifecycle.DRAFT,
                        false,
                        false,
                        false,
                        now,
                        now);
        var saved = definitions.save(definition);
        audit(
                saved,
                request.actorId(),
                "MODE_UPGRADE_TO_AUTOMATION",
                Map.of("sourceMode", "DELEGATED"));
        return saved;
    }

    public ImpactPreview preview(
            TenantId tenantId, String automationId, long version, String actorId) {
        var definition = requireVersion(tenantId, automationId, version);
        var policy = policies.get(tenantId);
        var prohibited =
                definition.permissionSnapshot().actions().stream()
                        .filter(policy.extremeRiskActions()::contains)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var preview =
                new ImpactPreview(
                        prohibited.isEmpty(),
                        definition.permissionSnapshot().actions(),
                        prohibited,
                        definition.template().contract().budget(),
                        definition.trigger());
        definitions.save(
                definition.assessed(preview.allowed(), definition.dryRunPassed(), clock.instant()));
        audit(
                definition,
                actorId,
                "IMPACT_PREVIEW",
                Map.of("allowed", preview.allowed(), "prohibited", prohibited));
        return preview;
    }

    public DryRunResult dryRun(
            TenantId tenantId,
            String automationId,
            long version,
            Map<String, Object> parameters,
            String actorId) {
        var definition = requireVersion(tenantId, automationId, version);
        definition.parameterSchema().validate(parameters);
        var policy = policies.get(tenantId);
        policy.requireUnattended(definition.permissionSnapshot().actions());
        var passed = definition.previewPassed();
        var result =
                new DryRunResult(
                        passed,
                        passed ? "dry-run 通过" : "必须先通过 impact preview",
                        definition.template().board().subTasks().size());
        definitions.save(definition.assessed(definition.previewPassed(), passed, clock.instant()));
        audit(definition, actorId, "DRY_RUN", Map.of("passed", passed));
        return result;
    }

    public AutomationDefinition publish(
            TenantId tenantId, String automationId, long version, String actorId) {
        var saved =
                definitions.save(
                        requireVersion(tenantId, automationId, version).publish(clock.instant()));
        audit(saved, actorId, "PUBLISH", Map.of());
        return saved;
    }

    public AutomationDefinition enable(TenantId tenantId, String automationId, String actorId) {
        var definition = requireLatest(tenantId, automationId);
        policies.get(tenantId).requireUnattended(definition.permissionSnapshot().actions());
        var saved = definitions.save(definition.enable(clock.instant()));
        audit(saved, actorId, "ENABLE", Map.of());
        return saved;
    }

    public AutomationDefinition disable(TenantId tenantId, String automationId, String actorId) {
        var saved =
                definitions.save(requireLatest(tenantId, automationId).disable(clock.instant()));
        audit(saved, actorId, "DISABLE", Map.of());
        return saved;
    }

    public AutomationRun trigger(
            TenantId tenantId,
            String automationId,
            String triggerKey,
            Map<String, Object> parameters,
            String actorId) {
        var definition = requireLatest(tenantId, automationId);
        if (!definition.enabled()) throw new IllegalStateException("自动化未启用");
        policies.get(tenantId).requireUnattended(definition.permissionSnapshot().actions());
        definition.parameterSchema().validate(parameters);
        var existing = runs.findByTrigger(tenantId, automationId, triggerKey);
        if (existing.isPresent()) return existing.get();
        var now = clock.instant();
        var runId = stableId(tenantId.value() + '|' + automationId + '|' + triggerKey);
        var taskId = new TaskId(stableId("task|" + runId));
        var run =
                new AutomationRun(
                        tenantId,
                        runId,
                        automationId,
                        definition.version(),
                        triggerKey,
                        taskId,
                        definition,
                        parameters == null ? Map.of() : parameters,
                        AutomationRun.Status.PENDING,
                        null,
                        now,
                        now);
        var saved = runs.createOnce(run);
        audit(
                definition,
                actorId,
                "TRIGGER",
                Map.of("triggerKey", triggerKey, "runId", saved.runId()));
        return saved;
    }

    public List<AutomationRun> history(TenantId tenantId, String automationId) {
        return runs.history(tenantId, automationId);
    }

    public OrganizationPolicy globalStop(TenantId tenantId, boolean stopped, String actorId) {
        var current = policies.get(tenantId);
        var saved =
                policies.save(
                        new OrganizationPolicy(
                                tenantId,
                                stopped,
                                current.extremeRiskActions(),
                                current.anomalyFailureThreshold(),
                                current.policyVersion(),
                                clock.instant()));
        audits.append(
                new AuditRecord(
                        tenantId,
                        UUID.randomUUID().toString(),
                        "*",
                        "GLOBAL_STOP_" + stopped,
                        actorId,
                        Map.of(),
                        clock.instant()));
        return saved;
    }

    public int dispatchPending(int limit) {
        var dispatched = 0;
        for (var run : runs.findPending(limit)) {
            try {
                policies.get(run.tenantId())
                        .requireUnattended(run.definitionSnapshot().permissionSnapshot().actions());
                dispatcher.dispatch(run);
                runs.markDispatched(run.tenantId(), run.runId(), clock.instant());
                dispatched++;
            } catch (RuntimeException failure) {
                runs.markFailed(run.tenantId(), run.runId(), failure.getMessage(), clock.instant());
                var policy = policies.get(run.tenantId());
                var failures =
                        runs.history(run.tenantId(), run.automationId()).stream()
                                .filter(
                                        candidate ->
                                                candidate.status() == AutomationRun.Status.FAILED)
                                .count();
                if (failures >= policy.anomalyFailureThreshold()) {
                    var disabled =
                            definitions.save(
                                    requireLatest(run.tenantId(), run.automationId())
                                            .disable(clock.instant()));
                    audit(
                            disabled,
                            "SYSTEM/anomaly-detector",
                            "ANOMALY_AUTO_DISABLE",
                            Map.of(
                                    "failureCount",
                                    failures,
                                    "threshold",
                                    policy.anomalyFailureThreshold()));
                }
            }
        }
        return dispatched;
    }

    public List<AutomationDefinition> list(TenantId tenantId) {
        return definitions.list(tenantId);
    }

    public List<AuditRecord> auditHistory(
            TenantId tenantId, String automationId, Instant from, Instant to) {
        return audits.search(tenantId, automationId, from, to);
    }

    private AutomationDefinition requireLatest(TenantId tenantId, String automationId) {
        return definitions
                .findLatest(tenantId, automationId)
                .orElseThrow(() -> new IllegalArgumentException("自动化不存在"));
    }

    private AutomationDefinition requireVersion(
            TenantId tenantId, String automationId, long version) {
        return definitions
                .findVersion(tenantId, automationId, version)
                .orElseThrow(() -> new IllegalArgumentException("自动化版本不存在"));
    }

    private void audit(
            AutomationDefinition definition,
            String actorId,
            String action,
            Map<String, Object> details) {
        audits.append(
                new AuditRecord(
                        definition.tenantId(),
                        UUID.randomUUID().toString(),
                        definition.automationId(),
                        action,
                        actorId,
                        details,
                        clock.instant()));
    }

    private static String stableId(String source) {
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static TaskBoard resetBoard(TaskBoard source, TaskId taskId) {
        var reset = new java.util.LinkedHashMap<String, TaskBoard.SubTask>();
        source.subTasks()
                .values()
                .forEach(
                        item ->
                                reset.put(
                                        item.subTaskId(),
                                        TaskBoard.SubTask.pending(
                                                item.subTaskId(),
                                                item.kind(),
                                                item.description(),
                                                item.dependsOn(),
                                                item.inputBindings(),
                                                item.roleKey(),
                                                item.skillKey(),
                                                item.modelSelection(),
                                                item.maxAttempts())));
        return new TaskBoard(taskId, source.goal(), source.maxParallelism(), reset);
    }

    public record DefineCommand(
            TenantId tenantId,
            String automationId,
            String name,
            TaskId sourceTaskId,
            ParameterSchema parameterSchema,
            TriggerSpec trigger,
            FailurePolicy failurePolicy,
            NotificationPlan notificationPlan,
            String policyVersion,
            String actorId) {}

    public record ImpactPreview(
            boolean allowed,
            Set<String> actions,
            Set<String> prohibitedActions,
            com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract.BudgetLimit
                    budget,
            TriggerSpec trigger) {}

    public record DryRunResult(boolean passed, String summary, int taskCount) {}
}
