package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import java.util.List;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.tool.ToolCatalogProvider;
import com.xuejiai.aaf.framework.engine.tool.ToolPermissionChecker;
import com.xuejiai.aaf.framework.engine.tool.ToolPermissionChecker.PermissionResult;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunContextHolder;
import com.xuejiai.aaf.framework.intelligent.assistant.HumanApprovalService;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.message.ToolUseBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 工具权限门控 Hook——PostReasoningEvent 时对每个工具调用做权限检查。
 *
 * <p>决策链（委托 ToolPermissionChecker）：
 * <ol>
 *   <li>deny 黑名单 → gotoReasoning（拒绝消息）
 *   <li>已授权 / readOnly / allowedTools → 继续执行
 *   <li>需要用户确认 → stopAgent()（Agent 暂停，等待 /agui/runs/{threadId}/confirm 恢复）
 * </ol>
 *
 * <p>替代 AafConfidenceHook，提供更精细的工具级权限控制。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AafToolPermissionHook implements Hook {

    private final ToolPermissionChecker permissionChecker;
    private final HumanApprovalService approvalService;
    private final ToolCatalogProvider toolCatalogProvider;

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostReasoningEvent postReasoning) {
            return checkPermissions(postReasoning).map(e -> event);
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 50; // 高于 AafToolWhitelistHook(20)，低于安全类
    }

    private Mono<PostReasoningEvent> checkPermissions(PostReasoningEvent event) {
        var msg = event.getReasoningMessage();
        if (msg == null || msg.getContent() == null) {
            return Mono.just(event);
        }

        var toolCalls = msg.getContentBlocks(ToolUseBlock.class);
        if (toolCalls.isEmpty()) {
            return Mono.just(event);
        }

        // 从 ThreadLocal 取运行上下文
        var ctx = AgentRunContextHolder.current().orElse(null);
        var sessionId = ctx != null ? ctx.runId() : null;
        var userId = ctx != null ? ctx.userId() : null;

        for (var toolUse : toolCalls) {
            var toolName = toolUse.getName();
            var catalogEntry = toolCatalogProvider.find(toolName).orElse(null);
            var riskLevel = catalogEntry != null ? catalogEntry.riskLevel() : ToolRiskLevel.MEDIUM;
            var readOnly = catalogEntry != null && catalogEntry.readOnly();

            var decision = permissionChecker.checkDetailed(
                    sessionId, userId, toolName,
                    riskLevel, readOnly, false,
                    List.of(), toolUse.getInput() != null ? toolUse.getInput().toString() : null);

            if (decision.result() == PermissionResult.DENIED) {
                log.info("工具调用被拒绝: {}", toolName);
                var denyMsg = io.agentscope.core.message.Msg.builder()
                        .name("system")
                        .role(io.agentscope.core.message.MsgRole.TOOL)
                        .content(io.agentscope.core.message.ToolResultBlock.of(
                                toolUse.getId(), toolName,
                                io.agentscope.core.message.TextBlock.builder()
                                        .text("工具调用被拒绝：" + decision.reason())
                                        .build()))
                        .build();
                event.gotoReasoning(denyMsg);
                return Mono.just(event);
            }

            if (decision.result() == PermissionResult.PENDING_APPROVAL) {
                log.info("工具调用需要用户确认，暂停 Agent: {}", toolName);
                // stopAgent() 暂停 Agent，等待 /agui/runs/{threadId}/confirm 恢复
                event.stopAgent();
                return Mono.just(event);
            }
            // GRANTED / AUTO_GRANTED → 继续执行
        }

        return Mono.just(event);
    }
}
