package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunContextHolder;
import com.xuejiai.aaf.framework.intelligent.agent.trace.ExecutionCompletedEvent;
import com.xuejiai.aaf.framework.intelligent.agent.trace.ExecutionStatus;
import com.xuejiai.aaf.framework.intelligent.agent.trace.StepType;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.message.ToolUseBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 执行轨迹记录 Hook——PostCallEvent 时采集完整执行轨迹，
 * 发布 ExecutionCompletedEvent 触发异步持久化（聊天记录、学习反馈、记忆写回）。
 *
 * <p>userId 和 conversationId 从 AgentRunContextHolder 取，
 * 由入口（/agui/runs 请求处理）在 open() 作用域内注入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AafTraceHook implements Hook {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostCallEvent postCall) {
            collectTrace(postCall);
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 900;
    }

    private void collectTrace(PostCallEvent event) {
        var agent = event.getAgent();
        var finalMsg = event.getFinalMessage();
        var memory = event.getMemory();

        // 从 ThreadLocal 取运行上下文
        var ctx = AgentRunContextHolder.current().orElse(null);
        var userId = ctx != null ? ctx.userId() : null;
        var conversationId = ctx != null ? ctx.runId() : null;

        // 提取工具调用记录
        List<ExecutionCompletedEvent.StepRecord> steps = List.of();
        if (memory != null) {
            var idx = new int[]{0};
            steps = memory.getMessages().stream()
                    .flatMap(msg -> msg.getContent().stream())
                    .filter(block -> block instanceof ToolUseBlock)
                    .map(block -> (ToolUseBlock) block)
                    .map(tu -> new ExecutionCompletedEvent.StepRecord(
                            idx[0]++, null, StepType.TOOL_CALL, agent.getName(),
                            tu.getName(), tu.getInput().toString(), null,
                            ExecutionStatus.SUCCESS, null, null, null))
                    .toList();
        }

        // 提取输入（第一条 USER 消息）
        String input = "";
        if (memory != null) {
            input = memory.getMessages().stream()
                    .filter(m -> m.getRole() == io.agentscope.core.message.MsgRole.USER
                            && m.getTextContent() != null)
                    .map(m -> m.getTextContent())
                    .findFirst()
                    .orElse("");
        }

        var completedEvent = new ExecutionCompletedEvent(
                UUID.randomUUID().toString(), null,
                agent.getName(), agent.getName(),
                userId, conversationId,
                input, finalMsg != null ? finalMsg.getTextContent() : "",
                ExecutionStatus.SUCCESS, null,
                0, 0,
                Instant.now(), Instant.now(),
                0, steps, Map.of());

        try {
            eventPublisher.publishEvent(completedEvent);
        } catch (Exception e) {
            log.warn("轨迹事件发布失败 agent={}: {}", agent.getName(), e.getMessage());
        }
    }
}
