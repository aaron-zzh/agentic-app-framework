package com.xuejiai.aaf.module.examples.agentscope.tools;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.xuejiai.aaf.framework.intelligent.agentscope.hook.TokenMeteringHook;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.agentscope.core.hook.ErrorEvent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.hook.PreCallEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 观察 Hook——演示 AgentScope Hook 机制的全部事件类型。
 *
 * <p>Hook 是 AgentScope 的切面扩展点，在 Agent 执行的各个阶段触发：
 *
 * <pre>
 * PreCallEvent       → Agent.call() 入口，可见输入消息
 * PreReasoningEvent  → LLM 推理前，可见发送给模型的完整消息列表
 * PostReasoningEvent → LLM 推理后，可见模型输出（含工具调用决策）
 * PreActingEvent     → 工具执行前，可见工具名和参数（可在此拦截/修改）
 * PostActingEvent    → 工具执行后，可见工具返回结果
 * PostCallEvent      → Agent.call() 出口，可见最终响应消息
 * ErrorEvent         → 任意阶段异常
 * </pre>
 *
 * <p>典型用途：链路追踪、审计日志、Token 计量、工具调用拦截（HITL）、性能监控。 AAF 的 {@link
 * TokenMeteringHook} 即基于 {@code PostCallEvent} 实现
 * Token 计量。
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "aaf.examples.agentscope.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class ObservationHook implements Hook {

    @Override
    public int priority() {
        // 低优先级，纯观察，不影响主流程
        return 900;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        // [Hook能力点] 按事件类型分发，覆盖 Agent 执行全生命周期
        if (event instanceof PreCallEvent e) {
            log.info("[Hook:PreCall] 输入消息数={}", e.getInputMessages().size());
        } else if (event instanceof PreReasoningEvent e) {
            // [Hook能力点] 可在此修改发送给 LLM 的消息（注入上下文、过滤敏感信息等）
            log.info(
                    "[Hook:PreReasoning] model={}, messages={}",
                    e.getModelName(),
                    e.getInputMessages().size());
        } else if (event instanceof PostReasoningEvent e) {
            // [Hook能力点] 可在此调用 e.stopAgent() 中断执行（HITL 工具确认场景）
            Msg msg = e.getReasoningMessage();
            if (msg != null) {
                List<ToolUseBlock> tools = msg.getContentBlocks(ToolUseBlock.class);
                if (!tools.isEmpty()) {
                    log.info(
                            "[Hook:PostReasoning] LLM 决定调用工具: {}",
                            tools.stream()
                                    .map(ToolUseBlock::getName)
                                    .collect(Collectors.joining(", ")));
                } else {
                    log.info("[Hook:PostReasoning] LLM 直接回复（无工具调用）");
                }
            }
        } else if (event instanceof PreActingEvent e) {
            // [Hook能力点] 工具执行前拦截点，可修改参数或拒绝执行
            ToolUseBlock tool = e.getToolUse();
            log.info(
                    "[Hook:PreActing] 即将执行工具: {}，参数: {}",
                    tool.getName(),
                    formatMap(tool.getInput()));
        } else if (event instanceof PostActingEvent e) {
            // [Hook能力点] 工具执行后，可记录结果或触发副作用
            ToolResultBlock result = e.getToolResult();
            log.info(
                    "[Hook:PostActing] 工具 {} 执行完成，结果: {}",
                    result.getName(),
                    truncate(extractText(result), 100));
        } else if (event instanceof PostCallEvent e) {
            // [Hook能力点] Agent 调用完成，TokenMeteringHook 在此读取 getChatUsage()
            Msg msg = e.getFinalMessage();
            if (msg != null && msg.getChatUsage() != null) {
                var usage = msg.getChatUsage();
                log.info(
                        "[Hook:PostCall] Token 用量 — 输入:{}, 输出:{}, 合计:{}",
                        usage.getInputTokens(),
                        usage.getOutputTokens(),
                        usage.getInputTokens() + usage.getOutputTokens());
            } else {
                log.info("[Hook:PostCall] Agent 调用完成");
            }
        } else if (event instanceof ErrorEvent e) {
            log.error(
                    "[Hook:Error] {}: {}",
                    e.getError().getClass().getSimpleName(),
                    e.getError().getMessage());
        }
        return Mono.just(event);
    }

    private String formatMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringJoiner sj = new StringJoiner(", ", "{", "}");
        map.forEach((k, v) -> sj.add(k + "=" + v));
        return sj.toString();
    }

    private String extractText(ToolResultBlock result) {
        if (result.getOutput() == null) return "";
        return result.getOutput().stream()
                .filter(TextBlock.class::isInstance)
                .map(b -> ((TextBlock) b).getText())
                .collect(Collectors.joining());
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }
}
