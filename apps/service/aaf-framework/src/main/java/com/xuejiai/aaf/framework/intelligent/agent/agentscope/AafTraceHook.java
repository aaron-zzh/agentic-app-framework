package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.cognition.learning.TrajectoryCollector;
import com.xuejiai.aaf.framework.intelligent.cognition.learning.TrajectoryCollector.Trajectory;
import com.xuejiai.aaf.framework.intelligent.cognition.learning.TrajectoryCollector.ToolCall;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.message.ToolUseBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 执行轨迹记录 Hook——PostCallEvent 时采集完整执行轨迹，
 * 委托 TrajectoryCollector 异步持久化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AafTraceHook implements Hook {

    private final TrajectoryCollector trajectoryCollector;

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostCallEvent postCall) {
            collectTrace(postCall);
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 900; // 低优先级，不影响主流程
    }

    private void collectTrace(PostCallEvent event) {
        var agent = event.getAgent();
        var finalMsg = event.getFinalMessage();
        var memory = event.getMemory();

        // 从 Memory 中提取工具调用记录
        List<ToolCall> toolCalls = List.of();
        if (memory != null) {
            toolCalls = memory.getMessages().stream()
                    .flatMap(msg -> msg.getContent().stream())
                    .filter(block -> block instanceof ToolUseBlock)
                    .map(block -> (ToolUseBlock) block)
                    .map(tu -> new ToolCall(tu.getName(), tu.getInput().toString(), null))
                    .toList();
        }

        // 提取输入（第一条 USER 消息）
        String input = "";
        if (memory != null) {
            input = memory.getMessages().stream()
                    .filter(m -> m.getTextContent() != null && m.getRole() == io.agentscope.core.message.MsgRole.USER)
                    .map(m -> m.getTextContent())
                    .findFirst()
                    .orElse("");
        }

        var trajectory = new Trajectory(
                UUID.randomUUID().toString(),
                agent.getName(),
                null,
                input,
                finalMsg != null ? finalMsg.getTextContent() : "",
                toolCalls,
                true,
                0L);

        try {
            trajectoryCollector.collect(trajectory);
        } catch (Exception e) {
            log.warn("轨迹采集失败 agent={}: {}", agent.getName(), e.getMessage());
        }
    }
}
