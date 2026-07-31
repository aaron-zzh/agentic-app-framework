package com.xuejiai.aaf.module.approval.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.approval.domain.ApprovalRecord;
import com.xuejiai.aaf.module.approval.service.ApprovalProcessService;
import com.xuejiai.aaf.module.approval.service.ApprovalRecordService;
import com.xuejiai.aaf.module.approval.service.DelegationService;
import com.xuejiai.aaf.module.approval.vo.ApprovalProcessInstanceVO;
import com.xuejiai.aaf.module.approval.vo.WorkflowActionDTO;
import com.xuejiai.aaf.module.approval.vo.WorkflowStartDTO;
import com.xuejiai.aaf.module.approval.vo.WorkflowStatusVO;
import com.xuejiai.aaf.module.approval.vo.WorkflowTaskVO;
import com.xuejiai.aaf.module.approval.vo.WorkflowTransferDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 审批流程及人工任务接口。 */
@Tag(name = "工作流审批")
@RestController
@RequestMapping("/api/system/workflow")
@RequiredArgsConstructor
public class ApprovalProcessController {

    private final ApprovalProcessService approvalProcessService;
    private final DelegationService delegationService;
    private final ApprovalRecordService approvalRecordService;
    private final OperatorContext operatorContext;

    @Operation(summary = "启动审批流程")
    @PostMapping("/start")
    public Result<String> start(@Validated @RequestBody WorkflowStartDTO dto) {
        var initiator = currentUserId();
        var processInstanceId =
                approvalProcessService.startProcess(
                        dto.entityType(), dto.entityId(), initiator, dto.assignee());
        return Result.success(processInstanceId);
    }

    @Operation(summary = "通过审批")
    @PostMapping("/complete")
    public Result<Void> complete(@Validated @RequestBody WorkflowActionDTO dto) {
        var userId = currentUserId();
        var processInstanceId =
                approvalProcessService.completeTask(dto.taskId(), userId, dto.comment());
        approvalRecordService.record(
                processInstanceId,
                dto.taskId(),
                userId,
                ApprovalRecord.OperationType.APPROVE,
                dto.comment());
        return Result.success();
    }

    @Operation(summary = "驳回审批")
    @PostMapping("/reject")
    public Result<Void> reject(@Validated @RequestBody WorkflowActionDTO dto) {
        var userId = currentUserId();
        var processInstanceId =
                approvalProcessService.rejectTask(dto.taskId(), userId, dto.comment());
        approvalRecordService.record(
                processInstanceId,
                dto.taskId(),
                userId,
                ApprovalRecord.OperationType.REJECT,
                dto.comment());
        return Result.success();
    }

    @Operation(summary = "查询流程状态")
    @GetMapping("/{processInstanceId}")
    public Result<WorkflowStatusVO> getStatus(@PathVariable String processInstanceId) {
        return Result.success(
                approvalProcessService.getStatus(processInstanceId, currentUserId()));
    }

    @Operation(summary = "按实体查询关联流程状态")
    @GetMapping("/status")
    public Result<WorkflowStatusVO> getStatusByEntity(
            @RequestParam String entityType, @RequestParam String entityId) {
        return Result.success(
                approvalProcessService.getStatusByEntity(entityType, entityId, currentUserId()));
    }

    @Operation(summary = "查询审批历史")
    @GetMapping("/{processInstanceId}/history")
    public Result<List<WorkflowStatusVO.HistoryItem>> getHistory(
            @PathVariable String processInstanceId) {
        return Result.success(
                approvalProcessService.getHistory(processInstanceId, currentUserId()));
    }

    @Operation(summary = "单次转交任务")
    @PostMapping("/transfer")
    public Result<Void> transfer(@Validated @RequestBody WorkflowTransferDTO dto) {
        delegationService.transfer(dto, currentUserId());
        return Result.success();
    }

    @Operation(summary = "我的待审批列表")
    @GetMapping("/tasks/my-pending")
    public Result<List<WorkflowTaskVO>> myPendingTasks() {
        return Result.success(approvalProcessService.listPendingTasks(currentUserId()));
    }

    @Operation(summary = "候选人待签收任务")
    @GetMapping("/tasks/candidate")
    public Result<List<WorkflowTaskVO>> listCandidateTasks() {
        return Result.success(approvalProcessService.listCandidateTasks(currentUserId()));
    }

    @Operation(summary = "候选组待签收任务")
    @GetMapping("/tasks/candidate-group")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<WorkflowTaskVO>> listCandidateGroupTasks(
            @RequestParam String candidateGroup) {
        return Result.success(approvalProcessService.listCandidateGroupTasks(candidateGroup));
    }

    @Operation(summary = "我发起的流程")
    @GetMapping("/instances/my-initiated")
    public Result<PageResult<ApprovalProcessInstanceVO>> listMyInitiatedInstances(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(
                approvalProcessService.listMyInitiatedInstances(
                        currentUserId(), pageNo, pageSize));
    }

    @Operation(summary = "签收任务")
    @PostMapping("/tasks/{taskId}/claim")
    public Result<Void> claimTask(@PathVariable String taskId) {
        approvalProcessService.claimTask(taskId, currentUserId());
        return Result.success();
    }

    @Operation(summary = "委派任务")
    @PostMapping("/tasks/{taskId}/delegate")
    public Result<Void> delegateTask(
            @PathVariable String taskId, @RequestParam String delegateUserId) {
        approvalProcessService.delegateTask(taskId, currentUserId(), delegateUserId);
        return Result.success();
    }

    @Operation(summary = "退回任务")
    @PostMapping("/tasks/{taskId}/return")
    public Result<Void> returnTask(
            @PathVariable String taskId, @RequestParam(required = false) String reason) {
        approvalProcessService.returnTask(taskId, currentUserId(), reason);
        return Result.success();
    }

    @Operation(summary = "催办任务")
    @PostMapping("/tasks/{taskId}/urge")
    public Result<Void> urgeTask(@PathVariable String taskId) {
        approvalProcessService.urgeTask(taskId, currentUserId());
        return Result.success();
    }

    private String currentUserId() {
        return operatorContext.currentUserId().orElseThrow().toString();
    }
}
