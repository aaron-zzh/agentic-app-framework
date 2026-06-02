package com.xuejiai.aaf.module.ai.agui;

import java.util.Map;

import com.xuejiai.aaf.module.ai.chat.agui.AgentRunEventStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.engine.tool.ToolPermissionChecker;
import com.xuejiai.aaf.framework.intelligent.agent.context.AgentRunContext;
import com.xuejiai.aaf.framework.intelligent.agent.context.AgentRunContextHolder;
import com.xuejiai.aaf.framework.intelligent.agent.trace.AgentRunEventPublisher;
import com.xuejiai.aaf.framework.intelligent.agent.trace.AgentRunEventType;
import com.xuejiai.aaf.framework.intelligent.assistant.hitl.HumanApprovalService;
import com.xuejiai.aaf.framework.security.OperatorContext;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AG-UI HITL 确认接口——用户确认/拒绝工具调用后恢复 Agent 执行。
 *
 * <p>流程：
 * <pre>
 * Agent 执行 → AafToolPermissionHook.stopAgent() 暂停
 *   → 前端收到 requires-action 状态，展示确认 UI
 *   → 用户确认 → POST /agui/runs/{threadId}/confirm { approved: true }
 *   → 找到暂停的 Agent → agent.stream(StreamOptions.defaults()) 恢复
 *   → 用户拒绝 → 注入 ToolResult（取消消息）→ agent.stream(cancelMsg) 继续
 * </pre>
 */
@Slf4j
@Tag(name = "AG-UI HITL")
@RestController
@RequestMapping("/agui/runs")
@RequiredArgsConstructor
public class AafAguiConfirmController {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    private final ThreadSessionManager threadSessionManager;
    private final ToolPermissionChecker permissionChecker;
    private final AgentRunEventPublisher agentRunEventPublisher;
    private final AgentRunEventStreamService agentRunEventStreamService;
    private final HumanApprovalService humanApprovalService;
    private final OperatorContext operatorContext;

    /** 确认/拒绝工具调用请求体 */
    public record ConfirmRequest(
            boolean approved,
            String reason,
            /** 被确认的工具调用 ID 列表（拒绝时用于构造 ToolResult） */
            java.util.List<ToolCallInfo> toolCalls) {}

    public record ToolCallInfo(String id, String name) {}

    @Operation(summary = "确认或拒绝工具调用，恢复暂停的 Agent")
    @PostMapping(
            value = "/{threadId}/confirm",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter confirm(
            @PathVariable String threadId,
            @RequestBody ConfirmRequest request) {

        var userId = operatorContext.currentUserId().orElse(null);
        var emitter = new SseEmitter(SSE_TIMEOUT);
        var runId = java.util.UUID.randomUUID().toString();
        agentRunEventStreamService.attach(runId, emitter, AgentRunEventStreamService.Format.AGUI_CUSTOM);

        // 找到暂停的 Agent
        var session = threadSessionManager.getSession(threadId).orElse(null);
        if (session == null || !(session.getAgent() instanceof ReActAgent reactAgent)) {
            try {
                emitter.send(SseEmitter.event().data("{\"error\":\"Agent 不存在或已过期\"}"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        Thread.startVirtualThread(() -> {
            try (var ignored = AgentRunContextHolder.open(runId, userId, null)) {
                agentRunEventPublisher.publish(
                        AgentRunEventType.RUN_STARTED, "恢复执行", "用户已确认工具调用",
                        Map.of("threadId", threadId, "approved", request.approved()));

                // 归档审批记录（通知层同步）
                var decision = request.approved()
                        ? HumanApprovalService.Decision.APPROVED
                        : HumanApprovalService.Decision.REJECTED;
                humanApprovalService.resolveBySession(threadId, userId, decision, request.reason());

                if (request.approved()) {
                    // 用户确认：授权工具，恢复执行
                    if (request.toolCalls() != null) {
                        request.toolCalls().forEach(tc ->
                                permissionChecker.grantWithScope(threadId, tc.name(),
                                        ToolPermissionChecker.GrantScope.ONCE, null));
                    }
                    reactAgent.stream(StreamOptions.defaults())
                            .doOnComplete(() -> {
                                agentRunEventPublisher.publish(
                                        new AgentRunContext(runId, userId, null),
                                        AgentRunEventType.RUN_FINISHED, "执行完成", "",
                                        Map.of());
                                emitter.complete();
                            })
                            .doOnError(e -> {
                                agentRunEventPublisher.publish(
                                        new AgentRunContext(runId, userId, null),
                                        AgentRunEventType.RUN_ERROR, "执行失败", e.getMessage(),
                                        Map.of());
                                emitter.completeWithError(e);
                            })
                            .subscribe();
                } else {
                    // 用户拒绝：注入取消消息，让 Agent 继续推理
                    var cancelReason = request.reason() != null ? request.reason() : "用户拒绝了工具调用";
                    var toolResults = request.toolCalls() != null
                            ? request.toolCalls().stream()
                                    .map(tc -> ToolResultBlock.of(tc.id(), tc.name(),
                                            TextBlock.builder().text(cancelReason).build()))
                                    .toList()
                            : java.util.List.<ToolResultBlock>of();

                    if (!toolResults.isEmpty()) {
                        var cancelMsg = Msg.builder()
                                .name("user").role(MsgRole.TOOL)
                                .content(toolResults.toArray(new ToolResultBlock[0]))
                                .build();
                        reactAgent.stream(cancelMsg)
                                .doOnComplete(() -> { emitter.complete(); })
                                .doOnError(e -> emitter.completeWithError(e))
                                .subscribe();
                    } else {
                        emitter.complete();
                    }
                }
            } catch (Exception e) {
                log.error("恢复 Agent 执行失败: threadId={}", threadId, e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Operation(summary = "中断 Agent 执行")
    @PostMapping("/{threadId}/interrupt")
    public Result<Void> interrupt(@PathVariable String threadId) {
        threadSessionManager.getSession(threadId)
                .map(ThreadSessionManager.ThreadSession::getAgent)
                .ifPresent(agent -> agent.interrupt());
        return Result.success();
    }
}
