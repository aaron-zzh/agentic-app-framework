package com.xuejiai.aaf.module.company.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.company.okr.service.CompanyOkrService;
import com.xuejiai.aaf.module.company.okr.vo.KeyResultCreateDTO;
import com.xuejiai.aaf.module.company.okr.vo.KeyResultVO;
import com.xuejiai.aaf.module.company.okr.vo.ObjectiveCreateDTO;
import com.xuejiai.aaf.module.company.okr.vo.ObjectiveVO;
import com.xuejiai.aaf.module.company.ops.service.CompanyOpsService;
import com.xuejiai.aaf.module.company.ops.vo.OpsMetricCreateDTO;
import com.xuejiai.aaf.module.company.ops.vo.OpsMetricVO;
import com.xuejiai.aaf.module.company.ops.vo.OpsTaskCreateDTO;
import com.xuejiai.aaf.module.company.ops.vo.OpsTaskExecutionVO;
import com.xuejiai.aaf.module.company.ops.vo.OpsTaskVO;
import com.xuejiai.aaf.module.company.planning.service.CompanyPlanService;
import com.xuejiai.aaf.module.company.planning.vo.CompanyPlanCreateDTO;
import com.xuejiai.aaf.module.company.planning.vo.CompanyPlanVO;
import com.xuejiai.aaf.module.company.workflow.WorkflowExecutor;
import com.xuejiai.aaf.module.company.workflow.WorkflowExecutor.WorkflowResult;
import com.xuejiai.aaf.module.company.workflow.WorkflowExecutor.WorkflowStep;

import lombok.RequiredArgsConstructor;

/** 企业智能运营统一接口 */
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CompanyController {

    private final CompanyPlanService planService;
    private final CompanyOkrService okrService;
    private final CompanyOpsService opsService;
    private final WorkflowExecutor workflowExecutor;

    // ===== 规划 =====

    @GetMapping("/plans")
    public Result<List<CompanyPlanVO>> listPlans() {
        return Result.success(planService.listPlans().stream().map(CompanyPlanVO::from).toList());
    }

    @PostMapping("/plans")
    public Result<CompanyPlanVO> createPlan(
            @Validated @RequestBody CompanyPlanCreateDTO request) {
        return Result.success(CompanyPlanVO.from(planService.createPlan(request)));
    }

    // ===== OKR =====

    @GetMapping("/okr/objectives")
    public Result<List<ObjectiveVO>> listObjectives(@RequestParam(required = false) String period) {
        return Result.success(
                okrService.listObjectives(period).stream().map(ObjectiveVO::from).toList());
    }

    @PostMapping("/okr/objectives")
    public Result<ObjectiveVO> createObjective(
            @Validated @RequestBody ObjectiveCreateDTO request) {
        return Result.success(ObjectiveVO.from(okrService.createObjective(request)));
    }

    @GetMapping("/okr/objectives/{id}/key-results")
    public Result<List<KeyResultVO>> listKeyResults(@PathVariable Long id) {
        return Result.success(
                okrService.listKeyResults(id).stream().map(KeyResultVO::from).toList());
    }

    @PostMapping("/okr/objectives/{id}/key-results")
    public Result<KeyResultVO> createKeyResult(
            @PathVariable Long id, @Validated @RequestBody KeyResultCreateDTO request) {
        return Result.success(KeyResultVO.from(okrService.createKeyResult(id, request)));
    }

    // ===== 运营任务 =====

    @GetMapping("/ops/tasks")
    public Result<List<OpsTaskVO>> listTasks() {
        return Result.success(opsService.listTasks().stream().map(OpsTaskVO::from).toList());
    }

    @PostMapping("/ops/tasks")
    public Result<OpsTaskVO> createTask(@Validated @RequestBody OpsTaskCreateDTO request) {
        return Result.success(OpsTaskVO.from(opsService.createTask(request)));
    }

    @PostMapping("/ops/tasks/{id}/execute")
    public Result<OpsTaskExecutionVO> executeTask(@PathVariable Long id) {
        return Result.success(OpsTaskExecutionVO.from(opsService.executeTask(id)));
    }

    @GetMapping("/ops/tasks/{id}/executions")
    public Result<Page<OpsTaskExecutionVO>> executions(@PathVariable Long id, Pageable pageable) {
        return Result.success(opsService.getExecutions(id, pageable).map(OpsTaskExecutionVO::from));
    }

    // ===== 指标 =====

    @GetMapping("/ops/metrics")
    public Result<List<OpsMetricVO>> listMetrics() {
        return Result.success(opsService.listMetrics().stream().map(OpsMetricVO::from).toList());
    }

    @PostMapping("/ops/metrics")
    public Result<OpsMetricVO> recordMetric(@Validated @RequestBody OpsMetricCreateDTO request) {
        return Result.success(OpsMetricVO.from(opsService.recordMetric(request)));
    }

    @GetMapping("/ops/metrics/{code}/history")
    public Result<List<OpsMetricVO>> metricHistory(@PathVariable String code) {
        return Result.success(
                opsService.getMetricHistory(code).stream().map(OpsMetricVO::from).toList());
    }

    // ===== 运营编排 =====

    /**
     * 执行运营编排——按 skill 串联的 AI 步骤链。
     *
     * <p>m15：这里的 "workflow" 指企业运营编排（{@code module.company.workflow}），不是 Flowable
     * 审批流，也不是 AI 工作流编辑器；边界说明见 {@link WorkflowExecutor}。
     *
     * <p>底层执行器 v1 已归档，当前调用会显式抛未实现异常——原注释声称的"fork 并行 + 置信度门控"并未实现， 不保留该描述以免误导调用方。
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
