package com.xuejiai.aaf.module.system.workflow.approval;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 审批流程控制器。
 *
 * @author Kiro
 */
@Tag(name = "审批流程")
@RestController
@RequestMapping("/api/system/workflow/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalOperationService approvalOperationService;
    private final ApprovalRecordService approvalRecordService;
    private final ApprovalPermissionService approvalPermissionService;
    private final CountersignService countersignService;

    // ==================== 加签/转签/撤回 ====================

    @Operation(summary = "前加签")
    @PostMapping("/add-sign-before")
    public Result<Void> addSignBefore(@RequestBody AddSignDTO dto) {
        approvalOperationService.addSignBefore(dto.taskId(), dto.assignee());
        approvalRecordService.record(
                null, dto.taskId(), dto.assignee(), ApprovalRecord.OperationType.ADD_SIGN, "前加签");
        return Result.success();
    }

    @Operation(summary = "后加签")
    @PostMapping("/add-sign-after")
    public Result<Void> addSignAfter(@RequestBody AddSignDTO dto) {
        approvalOperationService.addSignAfter(dto.taskId(), dto.assignee());
        approvalRecordService.record(
                null, dto.taskId(), dto.assignee(), ApprovalRecord.OperationType.ADD_SIGN, "后加签");
        return Result.success();
    }

    @Operation(summary = "转签")
    @PostMapping("/transfer")
    public Result<Void> transferSign(@RequestBody TransferSignDTO dto) {
        approvalOperationService.transferSign(dto.taskId(), dto.targetAssignee(), dto.reason());
        approvalRecordService.record(
                null,
                dto.taskId(),
                dto.targetAssignee(),
                ApprovalRecord.OperationType.TRANSFER,
                dto.reason());
        return Result.success();
    }

    @Operation(summary = "撤回")
    @PostMapping("/withdraw")
    public Result<Void> withdraw(@RequestBody WithdrawDTO dto) {
        approvalOperationService.withdraw(dto.processInstanceId(), dto.initiator());
        approvalRecordService.record(
                dto.processInstanceId(),
                null,
                dto.initiator(),
                ApprovalRecord.OperationType.WITHDRAW,
                "发起人撤回");
        return Result.success();
    }

    // ==================== 审批记录 ====================

    @Operation(summary = "查询审批时间线")
    @GetMapping("/timeline/{processInstanceId}")
    public Result<List<ApprovalRecordService.ApprovalRecordVO>> getTimeline(
            @PathVariable String processInstanceId) {
        return Result.success(approvalRecordService.getTimeline(processInstanceId));
    }

    // ==================== 会签 ====================

    @Operation(summary = "查询投票进度")
    @GetMapping("/vote-progress/{processInstanceId}")
    public Result<CountersignService.VoteProgress> getVoteProgress(
            @PathVariable String processInstanceId) {
        return Result.success(countersignService.getVoteProgress(processInstanceId));
    }

    // ==================== 权限与统计 ====================

    @Operation(summary = "审批统计")
    @GetMapping("/stats")
    public Result<ApprovalPermissionService.ApprovalStats> getStats(@RequestParam String assignee) {
        return Result.success(approvalPermissionService.getStats(assignee));
    }

    @Operation(summary = "检查审批权限")
    @GetMapping("/can-approve")
    public Result<Boolean> canApprove(@RequestParam Long userId, @RequestParam String processKey) {
        return Result.success(approvalPermissionService.canApprove(userId, processKey));
    }

    @Operation(summary = "检查是否为代理人")
    @GetMapping("/is-delegate")
    public Result<Boolean> isDelegate(
            @RequestParam Long currentUserId, @RequestParam Long delegatorId) {
        return Result.success(approvalPermissionService.isDelegateOf(currentUserId, delegatorId));
    }

    // ==================== DTO ====================

    /** 加签请求 */
    public record AddSignDTO(String taskId, String assignee) {}

    /** 转签请求 */
    public record TransferSignDTO(String taskId, String targetAssignee, String reason) {}

    /** 撤回请求 */
    public record WithdrawDTO(String processInstanceId, String initiator) {}
}
