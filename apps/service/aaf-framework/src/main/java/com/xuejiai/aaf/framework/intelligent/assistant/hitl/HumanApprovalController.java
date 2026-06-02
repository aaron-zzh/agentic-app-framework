package com.xuejiai.aaf.framework.intelligent.assistant.hitl;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** AI 人工确认请求接口。 */
@Tag(name = "AI 人工确认")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assistant/approvals")
@PreAuthorize("isAuthenticated()")
public class HumanApprovalController {

    private final HumanApprovalService approvalService;
    private final ApprovalEventStreamService eventStreamService;
    private final AssistantSessionTrustService sessionTrustService;
    private final OperatorContext operatorContext;

    /** 订阅当前用户的审批请求事件。 */
    @Operation(summary = "订阅审批请求事件")
    @GetMapping("/events")
    public SseEmitter events() {
        return eventStreamService.subscribe(currentUserId());
    }

    /** 查询当前用户待处理的审批请求。 */
    @Operation(summary = "查询待处理审批请求")
    @GetMapping
    public Result<List<HumanApprovalService.ApprovalRequest>> pending() {
        return Result.success(approvalService.getPending(currentUserId()));
    }

    /** 处理审批请求。 */
    @Operation(summary = "处理审批请求")
    @PostMapping("/{requestId}/resolve")
    public Result<Void> resolve(
            @PathVariable String requestId, @Valid @RequestBody ResolveApprovalRequest request) {
        approvalService.resolve(requestId, currentUserId(), request.decision(), request.reason());
        return Result.success();
    }

    /** 信任会话内指定工具。 */
    @Operation(summary = "信任会话内指定工具")
    @PostMapping("/sessions/{sessionId}/trust-tools")
    public Result<Void> trustTools(
            @PathVariable String sessionId, @Valid @RequestBody TrustToolsRequest request) {
        sessionTrustService.trustTools(sessionId, currentUserId(), request.toolNames());
        return Result.success();
    }

    /** 授予当前会话全量委托，仍受委托者实际权限和风险门控约束。 */
    @Operation(summary = "授予当前会话全量委托")
    @PostMapping("/sessions/{sessionId}/grant-full-delegation")
    public Result<Void> grantFullDelegation(@PathVariable String sessionId) {
        sessionTrustService.grantFullDelegation(sessionId, currentUserId());
        return Result.success();
    }

    /** 撤销当前会话全量委托。 */
    @Operation(summary = "撤销当前会话全量委托")
    @PostMapping("/sessions/{sessionId}/revoke-full-delegation")
    public Result<Void> revokeFullDelegation(@PathVariable String sessionId) {
        sessionTrustService.revokeFullDelegation(sessionId);
        return Result.success();
    }

    private Long currentUserId() {
        return operatorContext.currentOwnerId().orElseThrow();
    }

    public record ResolveApprovalRequest(
            @NotNull HumanApprovalService.Decision decision, String reason) {}

    public record TrustToolsRequest(@NotNull List<String> toolNames) {}
}
