package com.xuejiai.aaf.module.tool;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.assistant.hitl.ToolApprovalService;
import com.xuejiai.aaf.framework.security.OperatorContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 工具层人工确认接口（M36）。
 *
 * <p>补齐旧内存版 HITL 缺失的处理入口——此前审批只能被创建、无任何 API 可以处理，用户在渠道卡片上 点"同意"也无处落地。任务级审批仍走 {@code
 * HumanApprovalController}，两者作用域不同。
 *
 * <p>所有操作只作用于当前登录用户自己的审批（服务层校验 userId 归属）。
 */
@Tag(name = "工具人工确认")
@RestController
@RequestMapping("/api/tool-approvals")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ToolApprovalController {

    private final ToolApprovalService approvalService;
    private final OperatorContext operatorContext;

    @Operation(summary = "查询我的待处理工具审批")
    @GetMapping("/pending")
    public Result<List<ToolApprovalService.ApprovalRequest>> pending() {
        return Result.success(approvalService.getPending(currentUserId()));
    }

    @Operation(summary = "处理工具审批（同意/拒绝）")
    @PostMapping("/{approvalId}/decide")
    public Result<Void> decide(
            @PathVariable String approvalId,
            @RequestParam boolean approved,
            @RequestParam(required = false) String reason) {
        approvalService.decide(
                approvalId,
                currentUserId(),
                approved
                        ? ToolApprovalService.Decision.APPROVED
                        : ToolApprovalService.Decision.REJECTED,
                reason);
        return Result.success();
    }

    @Operation(summary = "按会话批量处理工具审批（AG-UI confirm 场景）")
    @PostMapping("/sessions/{sessionId}/decide")
    public Result<Void> decideByScope(
            @PathVariable String sessionId,
            @RequestParam boolean approved,
            @RequestParam(required = false) String reason) {
        approvalService.decideByScope(
                sessionId,
                currentUserId(),
                approved
                        ? ToolApprovalService.Decision.APPROVED
                        : ToolApprovalService.Decision.REJECTED,
                reason);
        return Result.success();
    }

    private Long currentUserId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(
                        () ->
                                new com.xuejiai.aaf.common.exception.BusinessException(
                                        com.xuejiai.aaf.common.exception.GlobalErrorCode
                                                .UNAUTHORIZED));
    }
}
