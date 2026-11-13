package com.xuejiai.aaf.module.ai.assistant.controller;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.ai.assistant.AssistantErrorCode.PROGRAMMATIC_TASK_CONCURRENT_LIMIT_EXCEEDED;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.assistant.application.TaskCommandService;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDetails;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HumanApprovalPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskQueryPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.billing.api.ParallelTaskQuotaApi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 当前认证用户的 canonical Task/Plan/Execution/Dispatch 查询入口。 */
@Tag(name = "Task")
@RestController
@RequestMapping("/api/ai/tasks")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TaskController {
    /** 首页看板单次最多返回的未终结 Task 数。 */
    private static final int ACTIVE_TASK_LIMIT = 50;

    private final TaskQueryPort tasks;
    private final HumanApprovalPort approvals;
    private final TaskCommandService commands;
    private final OperatorContext operatorContext;
    private final ParallelTaskQuotaApi parallelTaskQuota;

    @Operation(summary = "查询当前用户在指定对话中的 Task 列表")
    @GetMapping
    public Result<List<TaskDetails>> list(@RequestParam("conversationId") String conversationId) {
        var tenantId = tenantId();
        return Result.success(
                tasks.list(tenantId, userId(), new ConversationId(conversationId)).stream()
                        .map(details -> withDecisionBlockers(tenantId, details))
                        .toList());
    }

    @Operation(summary = "查询当前用户跨会话的未终结 Task（首页看板，最多 50 条，按更新时间倒序）")
    @GetMapping("/active")
    public Result<List<TaskDetails>> active() {
        var tenantId = tenantId();
        return Result.success(
                tasks.listActive(tenantId, userId(), ACTIVE_TASK_LIMIT).stream()
                        .map(details -> withDecisionBlockers(tenantId, details))
                        .toList());
    }

    @Operation(summary = "查询 Task 及 current plan、attempt 与 dispatch")
    @GetMapping("/{taskId}")
    public Result<TaskDetails> get(@PathVariable String taskId) {
        var tenantId = tenantId();
        return Result.success(
                tasks.find(tenantId, userId(), new TaskId(taskId))
                        .map(details -> withDecisionBlockers(tenantId, details))
                        .orElseThrow(() -> new IllegalArgumentException("Task 不存在")));
    }

    @Operation(summary = "查询 Task 当前待处理的工具授权（后台执行的任务无实时流时，由任务卡片发起审批）")
    @GetMapping("/{taskId}/approvals")
    public Result<List<PendingApprovalVO>> pendingApprovals(@PathVariable String taskId) {
        var tenantId = tenantId();
        var userId = userId();
        var canonicalTaskId = new TaskId(taskId);
        // 先按当前用户查 Task，确保只能看到自己任务的审批
        tasks.find(tenantId, userId, canonicalTaskId)
                .orElseThrow(() -> new IllegalArgumentException("Task 不存在"));
        return Result.success(
                approvals.pending(tenantId, canonicalTaskId).stream()
                        .filter(approval -> approval.invocationContext().userId().equals(userId))
                        .map(
                                approval ->
                                        new PendingApprovalVO(
                                                approval.approvalId(),
                                                approval.action(),
                                                approval.reason(),
                                                approval.impact(),
                                                approval.reversible()))
                        .toList());
    }

    /** 待处理授权的展示投影：只暴露决策所需信息，不含调用上下文与参数原文。 */
    public record PendingApprovalVO(
            String approvalId, String action, String reason, String impact, boolean reversible) {}

    @Operation(summary = "请求暂停 Task，立即关闭旧执行权并等待逐 Execution 状态保存 ACK")
    @PostMapping("/{taskId}/pause")
    public Result<TaskDetails> pause(
            @PathVariable String taskId, @RequestBody PauseRequest request) {
        var tenantId = tenantId();
        var userId = userId();
        var canonicalTaskId = new TaskId(taskId);
        commands.pause(tenantId, userId, canonicalTaskId, request.reason());
        return Result.success(
                tasks.find(tenantId, userId, canonicalTaskId)
                        .map(details -> withDecisionBlockers(tenantId, details))
                        .orElseThrow(() -> new IllegalStateException("暂停后的 Task 不存在")));
    }

    @Operation(summary = "恢复已暂停 Task，按持久 resume mode 复用 attempt 或创建 fresh attempt")
    @PostMapping("/{taskId}/resume")
    public Result<TaskDetails> resume(@PathVariable String taskId) {
        var tenantId = tenantId();
        var userId = userId();
        var canonicalTaskId = new TaskId(taskId);
        requireAutomationConcurrencySlot(tenantId, userId, canonicalTaskId);
        commands.resume(tenantId, userId, canonicalTaskId);
        return Result.success(
                tasks.find(tenantId, userId, canonicalTaskId)
                        .map(details -> withDecisionBlockers(tenantId, details))
                        .orElseThrow(() -> new IllegalStateException("恢复后的 Task 不存在")));
    }

    @Operation(summary = "请求人工接管 Task，稳定暂停后原子切换责任主体")
    @PostMapping("/{taskId}/take-over")
    public Result<TaskDetails> takeOver(
            @PathVariable String taskId, @RequestBody TakeOverRequest request) {
        var tenantId = tenantId();
        var userId = userId();
        var canonicalTaskId = new TaskId(taskId);
        commands.takeOver(tenantId, userId, canonicalTaskId, request.reason());
        return Result.success(
                tasks.find(tenantId, userId, canonicalTaskId)
                        .map(details -> withDecisionBlockers(tenantId, details))
                        .orElseThrow(() -> new IllegalStateException("接管后的 Task 不存在")));
    }

    @Operation(summary = "把人工接管的 Task 交回合同 Owner Assistant 并创建 fresh attempt")
    @PostMapping("/{taskId}/hand-back")
    public Result<TaskDetails> handBack(@PathVariable String taskId) {
        var tenantId = tenantId();
        var userId = userId();
        var canonicalTaskId = new TaskId(taskId);
        requireAutomationConcurrencySlot(tenantId, userId, canonicalTaskId);
        commands.handBack(tenantId, userId, canonicalTaskId);
        return Result.success(
                tasks.find(tenantId, userId, canonicalTaskId)
                        .map(details -> withDecisionBlockers(tenantId, details))
                        .orElseThrow(() -> new IllegalStateException("交回后的 Task 不存在")));
    }

    @Operation(summary = "请求取消 Task，立即关闭旧执行权并异步终结")
    @PostMapping("/{taskId}/cancel")
    public Result<TaskDetails> cancel(
            @PathVariable String taskId, @RequestBody CancelRequest request) {
        var tenantId = tenantId();
        var userId = userId();
        var canonicalTaskId = new TaskId(taskId);
        commands.cancel(tenantId, userId, canonicalTaskId, request.reason());
        return Result.success(
                tasks.find(tenantId, userId, canonicalTaskId)
                        .map(details -> withDecisionBlockers(tenantId, details))
                        .orElseThrow(() -> new IllegalStateException("取消后的 Task 不存在")));
    }

    /** PAUSED 自动化任务已释放名额，恢复执行前必须按当前订阅重新准入。 */
    private void requireAutomationConcurrencySlot(TenantId tenantId, UserId userId, TaskId taskId) {
        var task = tasks.find(tenantId, userId, taskId).orElse(null);
        if (task == null
                || !Task.Source.AUTOMATION.name().equals(task.source())
                || !Task.Status.PAUSED.name().equals(task.status())) {
            return;
        }
        var ownerId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(() -> new AccessDeniedException("请求未认证"));
        var limit = parallelTaskQuota.parallelTaskLimit(ownerId);
        if (limit != -1L
                && tasks.countActiveBySource(tenantId, userId, Task.Source.AUTOMATION) >= limit) {
            throw exception(PROGRAMMATIC_TASK_CONCURRENT_LIMIT_EXCEEDED);
        }
    }

    /**
     * 补上待人工决策阻断原因。
     *
     * <p>阻断原因需要用权威 evaluator 对等待外部结果的执行重新求值，属应用层职责，查询适配器不计算；终态 Task 不可能在 等待外部结果，直接返回空，避免无意义的求值查询。
     */
    private TaskDetails withDecisionBlockers(TenantId tenantId, TaskDetails details) {
        if (details.terminal()) {
            return details;
        }
        return details.withDecisionBlockers(
                commands.externalDecisionBlockers(tenantId, new TaskId(details.taskId())));
    }

    private static TenantId tenantId() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) {
            throw new AccessDeniedException("请求缺少已校验的组织上下文");
        }
        return new TenantId(orgId.toString());
    }

    private UserId userId() {
        return new UserId(
                operatorContext
                        .currentOwnerId()
                        .map(String::valueOf)
                        .orElseThrow(() -> new AccessDeniedException("请求未认证")));
    }

    public record PauseRequest(String reason) {
        public PauseRequest {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("暂停原因不能为空白");
            }
            reason = reason.trim();
        }
    }

    public record TakeOverRequest(String reason) {
        public TakeOverRequest {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("接管原因不能为空白");
            }
            reason = reason.trim();
        }
    }

    public record CancelRequest(String reason) {
        public CancelRequest {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("取消原因不能为空白");
            }
            reason = reason.trim();
        }
    }
}
