package com.xuejiai.aaf.module.ai.assistant.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
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
import com.xuejiai.aaf.module.ai.chat.agui.AgUiEvent;

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
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "仅批准决定可恢复执行");
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
        stream.events()
                .subscribe(
                        stored ->
                                send(
                                        emitter,
                                        agUiProjector.project(stored.event()),
                                        stored.eventOffset()),
                        failure -> sendError(emitter, stream.runId()),
                        emitter::complete);
        return emitter;
    }

    private static void send(SseEmitter emitter, List<AgUiEvent> events, long cursor) {
        try {
            for (var event : events) {
                emitter.send(
                        SseEmitter.event()
                                .id(Long.toString(cursor))
                                .name(event.type())
                                .data(event.toMap(), MediaType.APPLICATION_JSON));
            }
        } catch (IOException failure) {
            emitter.complete();
        }
    }

    private static void sendError(SseEmitter emitter, String runId) {
        try {
            var event = AgUiEvent.runError(runId, "Assistant 运行未完成");
            emitter.send(
                    SseEmitter.event()
                            .name(event.type())
                            .data(event.toMap(), MediaType.APPLICATION_JSON));
        } catch (IOException failure) {
            // 客户端已断开时无需继续发送。
        } finally {
            emitter.complete();
        }
    }

    private com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval ownedApproval(
            String approvalId) {
        var approval =
                approvals
                        .find(currentTenant(), approvalId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "审批不存在"));
        if (!approval.invocationContext().userId().value().equals(currentUser())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        return approval;
    }

    private String currentUser() {
        return operatorContext
                .currentOwnerId()
                .map(String::valueOf)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    private TenantId currentTenant() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "请求缺少已校验的组织上下文");
        }
        return new TenantId(orgId.toString());
    }
}
