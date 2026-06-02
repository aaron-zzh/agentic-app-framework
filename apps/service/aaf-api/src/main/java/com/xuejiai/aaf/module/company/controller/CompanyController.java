package com.xuejiai.aaf.module.company.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.company.okr.domain.KeyResult;
import com.xuejiai.aaf.module.company.okr.domain.Objective;
import com.xuejiai.aaf.module.company.okr.service.CompanyOkrService;
import com.xuejiai.aaf.module.company.ops.domain.OpsMetric;
import com.xuejiai.aaf.module.company.ops.domain.OpsTask;
import com.xuejiai.aaf.module.company.ops.domain.OpsTaskExecution;
import com.xuejiai.aaf.module.company.ops.service.CompanyOpsService;
import com.xuejiai.aaf.module.company.planning.domain.CompanyPlan;
import com.xuejiai.aaf.module.company.planning.service.CompanyPlanService;
import com.xuejiai.aaf.module.company.workflow.WorkflowExecutor;
import com.xuejiai.aaf.module.company.workflow.WorkflowExecutor.WorkflowResult;
import com.xuejiai.aaf.module.company.workflow.WorkflowExecutor.WorkflowStep;

import lombok.RequiredArgsConstructor;

/** 企业智能运营统一接口 */
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyPlanService planService;
    private final CompanyOkrService okrService;
    private final CompanyOpsService opsService;
    private final WorkflowExecutor workflowExecutor;

    // ===== 规划 =====

    @GetMapping("/plans")
    public Result<List<CompanyPlan>> listPlans() {
        return Result.success(planService.listPlans());
    }

    @PostMapping("/plans")
    public Result<CompanyPlan> createPlan(@RequestBody CompanyPlan plan) {
        return Result.success(planService.createPlan(plan));
    }

    // ===== OKR =====

    @GetMapping("/okr/objectives")
    public Result<List<Objective>> listObjectives(@RequestParam(required = false) String period) {
        return Result.success(okrService.listObjectives(period));
    }

    @PostMapping("/okr/objectives")
    public Result<Objective> createObjective(@RequestBody Objective objective) {
        return Result.success(okrService.createObjective(objective));
    }

    @GetMapping("/okr/objectives/{id}/key-results")
    public Result<List<KeyResult>> listKeyResults(@PathVariable Long id) {
        return Result.success(okrService.listKeyResults(id));
    }

    @PostMapping("/okr/objectives/{id}/key-results")
    public Result<KeyResult> createKeyResult(@PathVariable Long id, @RequestBody KeyResult kr) {
        kr.setObjectiveId(id);
        return Result.success(okrService.createKeyResult(kr));
    }

    // ===== 运营任务 =====

    @GetMapping("/ops/tasks")
    public Result<List<OpsTask>> listTasks() {
        return Result.success(opsService.listTasks());
    }

    @PostMapping("/ops/tasks")
    public Result<OpsTask> createTask(@RequestBody OpsTask task) {
        return Result.success(opsService.createTask(task));
    }

    @PostMapping("/ops/tasks/{id}/execute")
    public Result<OpsTaskExecution> executeTask(@PathVariable Long id) {
        return Result.success(opsService.executeTask(id));
    }

    @GetMapping("/ops/tasks/{id}/executions")
    public Result<Page<OpsTaskExecution>> executions(@PathVariable Long id, Pageable pageable) {
        return Result.success(opsService.getExecutions(id, pageable));
    }

    // ===== 指标 =====

    @GetMapping("/ops/metrics")
    public Result<List<OpsMetric>> listMetrics() {
        return Result.success(opsService.listMetrics());
    }

    @PostMapping("/ops/metrics")
    public Result<OpsMetric> recordMetric(@RequestBody OpsMetric metric) {
        return Result.success(opsService.recordMetric(metric));
    }

    @GetMapping("/ops/metrics/{code}/history")
    public Result<List<OpsMetric>> metricHistory(@PathVariable String code) {
        return Result.success(opsService.getMetricHistory(code));
    }

    // ===== 工作流编排（演示多种编排模式） =====

    /**
     * 执行工作流——演示助理多角色编排能力。
     *
     * <p>编排模式：顺序 + fork 并行 + 结果聚合 + 置信度门控
     */
    @PostMapping("/workflow/execute")
    public Result<WorkflowResult> executeWorkflow(@RequestBody WorkflowRequest request) {
        var steps =
                request.steps().stream()
                        .map(
                                s ->
                                        new WorkflowStep(
                                                s.skill(),
                                                s.name(),
                                                s.input(),
                                                s.output(),
                                                s.dependsOn()))
                        .toList();
        return Result.success(
                workflowExecutor.execute(request.sessionId(), steps, request.input()));
    }

    record WorkflowStepDTO(
            String skill, String name, String input, String output, List<String> dependsOn) {
        WorkflowStepDTO(String skill, String name, String input, String output) {
            this(skill, name, input, output, List.of());
        }
    }

    record WorkflowRequest(String sessionId, String input, List<WorkflowStepDTO> steps) {}
}
