package com.xuejiai.aaf.module.ai.assistant.controller;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.ai.assistant.AssistantErrorCode.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlCoordinatorPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HitlCoordinatorPort.DecisionCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HumanApprovalPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskRecoveryDispatchPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.agui.AgUiProjector;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantApprovalEventService;
import com.xuejiai.aaf.module.ai.assistant.vo.HumanApprovalDecisionDTO;
import com.xuejiai.aaf.module.ai.assistant.vo.HumanApprovalVO;
import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.agentscope.core.agui.event.AguiEvent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** P3 通用人工审批入口。 */
@Tag(name = "Assistant 人工审批")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
public class HumanApprovalController {
    /** 线程安全且无状态，可跨请求共享。 */
    private static final AguiEventEncoder ENCODER = new AguiEventEncoder();

    private final HitlCoordinatorPort hitl;
    private final HumanApprovalPort approvals;
    private final TaskRecoveryDispatchPort recoveries;
    private final AssistantApprovalEventService approvalEvents;
    private final AgUiProjector agUiProjector;
    private final OperatorContext operatorContext;

    @Operation(summary = "查询当前用户的待处理审批")
    @GetMapping("/ai/approvals/pending")
    public Result<List<HumanApprovalVO>> pending() {
        var pending =
                approvals.pending(currentTenant(), new UserId(currentUser())).stream()
                        .map(HumanApprovalVO::from)
                        .toList();
        return Result.success(pending);
    }

    @Operation(summary = "批准或拒绝受控工具动作")
    @PostMapping("/ai/approvals/{approvalId}/decision")
    public Result<HumanApprovalVO> decide(
            @PathVariable String approvalId,
            @Validated @RequestBody HumanApprovalDecisionDTO request) {
        var tenantId = currentTenant();
        var userId = currentUser();
        var approval =
                hitl.decide(
                        new DecisionCommand(
                                tenantId,
                                approvalId,
                                request.decision(),
                                userId,
                                request.reason(),
                                Instant.now()));
        return Result.success(HumanApprovalVO.from(approval));
    }

    @Operation(summary = "按 approvalId 重放未完成的批准恢复作业")
    @PostMapping("/ai/approvals/{approvalId}/recover")
    public Result<Boolean> recover(@PathVariable String approvalId) {
        var approval = ownedApproval(approvalId);
        if (approval.status()
                != com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval.Status
                        .APPROVED) {
            throw exception(APPROVAL_RECOVERY_REQUIRES_APPROVED);
        }
        return Result.success(recoveries.recover(currentTenant(), approvalId));
    }

    @Operation(summary = "续读批准后的 Assistant AG-UI 恢复事件")
    @GetMapping(
            value = "/agui/approvals/{approvalId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String approvalId) {
        var stream = approvalEvents.stream(ownedApproval(approvalId));
        var emitter = new SseEmitter(300_000L);
        var session = agUiProjector.openSession();
        var runId = stream.runId();
        stream.events()
                .subscribe(
                        stored ->
                                send(
                                        emitter,
                                        session.project(stored.event()),
                                        stored.eventOffset()),
                        failure -> {
                            send(
                                    emitter,
                                    session.fail(runId, runId, "ASSISTANT_STREAM_FAILED"),
                                    0);
                            emitter.complete();
                        },
                        () -> {
                            send(emitter, session.close(runId, runId), Long.MAX_VALUE);
                            emitter.complete();
                        });
        return emitter;
    }

    // TODO(agentscope-boundary): 该恢复端点应替换为 AG-UI 原生 interrupt/resume——
    // RUN_FINISHED.outcome={type:"interrupt",interrupts:[...]} + 客户端 runAgent({resume:[...]})，
    // 届时本端点与前端 streamApprovedAssistantAgUi 一并删除。当前 close/fail 用 runId 兼作 threadId
    // 是权宜之计（本端点无会话上下文），迁移后由统一入口提供真实 threadId。
    // 依据：docs/design/audit/2026-08-30-agentscope-boundary/03-capability-gap.md 后续修正
    private static void send(SseEmitter emitter, List<AguiEvent> events, long cursor) {
        try {
            for (var index = 0; index < events.size(); index++) {
                emitter.send(
                        SseEmitter.event()
                                .id(cursor + "." + index)
                                .data(ENCODER.encodeToJson(events.get(index))));
            }
        } catch (IOException failure) {
            emitter.complete();
        }
    }

    private com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval ownedApproval(
            String approvalId) {
        var approval =
                approvals
                        .find(currentTenant(), approvalId)
                        .orElseThrow(() -> exception(APPROVAL_NOT_FOUND));
        if (!approval.invocationContext().userId().value().equals(currentUser())) {
            throw new AccessDeniedException("无权访问该审批");
        }
        return approval;
    }

    private String currentUser() {
        return operatorContext
                .currentOwnerId()
                .map(String::valueOf)
                .orElseThrow(() -> new AccessDeniedException("请求未认证"));
    }

    private TenantId currentTenant() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) {
            throw new AccessDeniedException("请求缺少已校验的组织上下文");
        }
        return new TenantId(orgId.toString());
    }
}
