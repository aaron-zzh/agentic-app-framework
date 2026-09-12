package com.xuejiai.aaf.module.ai.assistant.controller;

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
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDetails;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskQueryPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

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
    private final TaskQueryPort tasks;
    private final TaskCommandService commands;
    private final OperatorContext operatorContext;

    @Operation(summary = "查询当前用户在指定对话中的 Task 列表")
    @GetMapping
    public Result<List<TaskDetails>> list(@RequestParam("conversationId") String conversationId) {
        return Result.success(tasks.list(tenantId(), userId(), new ConversationId(conversationId)));
    }

    @Operation(summary = "查询 Task 及 current plan、attempt 与 dispatch")
    @GetMapping("/{taskId}")
    public Result<TaskDetails> get(@PathVariable String taskId) {
        return Result.success(
                tasks.find(tenantId(), userId(), new TaskId(taskId))
                        .orElseThrow(() -> new IllegalArgumentException("Task 不存在")));
    }

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
                        .orElseThrow(() -> new IllegalStateException("暂停后的 Task 不存在")));
    }

    @Operation(summary = "恢复已暂停 Task，按持久 resume mode 复用 attempt 或创建 fresh attempt")
    @PostMapping("/{taskId}/resume")
    public Result<TaskDetails> resume(@PathVariable String taskId) {
        var tenantId = tenantId();
        var userId = userId();
        var canonicalTaskId = new TaskId(taskId);
        commands.resume(tenantId, userId, canonicalTaskId);
        return Result.success(
                tasks.find(tenantId, userId, canonicalTaskId)
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
                        .orElseThrow(() -> new IllegalStateException("接管后的 Task 不存在")));
    }

    @Operation(summary = "把人工接管的 Task 交回合同 Owner Assistant 并创建 fresh attempt")
    @PostMapping("/{taskId}/hand-back")
    public Result<TaskDetails> handBack(@PathVariable String taskId) {
        var tenantId = tenantId();
        var userId = userId();
        var canonicalTaskId = new TaskId(taskId);
        commands.handBack(tenantId, userId, canonicalTaskId);
        return Result.success(
                tasks.find(tenantId, userId, canonicalTaskId)
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
                        .orElseThrow(() -> new IllegalStateException("取消后的 Task 不存在")));
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
