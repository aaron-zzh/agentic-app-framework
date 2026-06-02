package com.xuejiai.aaf.framework.engine.meta;

import java.util.List;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.agent.AgentRegistryService;
import com.xuejiai.aaf.framework.intelligent.agent.runtime.AgentPool;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantExecutor;
import com.xuejiai.aaf.framework.intelligent.core.confidence.ConfidenceGate;
import com.xuejiai.aaf.framework.intelligent.core.confidence.ConfidenceGate.GateInput;
import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient;
import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient.LlmMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 元引擎执行调度器——统一调度入口，路由到 Agent/Assistant/LLM。
 *
 * <p>所有执行请求（Flowable 节点、对话意图、DSL 指令、API 调用）统一经此调度。 职责：路由 + 置信度门控 + 执行记录。不做图遍历（由 Flowable
 * 或未来编排运行时负责）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionDispatcher {

    private final AgentPool agentPool;
    private final AgentRegistryService agentRegistry;
    private final AssistantExecutor assistantExecutor;
    private final LlmClient llmClient;
    private final ConfidenceGate confidenceGate;

    /** 执行请求 */
    public record ExecutionRequest(
            ExecutionTarget target,
            String targetId,
            String input,
            String sessionId,
            Long userId,
            String systemPrompt,
            double confidence,
            boolean verifiable) {

        public ExecutionRequest(ExecutionTarget target, String targetId, String input) {
            this(target, targetId, input, null, null, null, 0.9, true);
        }
    }

    /** 执行目标类型 */
    public enum ExecutionTarget {
        AGENT,
        ASSISTANT,
        LLM
    }

    /** 统一执行结果 */
    public record ExecutionResult(boolean success, String output, String error) {}

    /** 调度执行——统一入口。 */
    public ExecutionResult dispatch(ExecutionRequest request) {
        // 置信度门控
        var decision =
                confidenceGate.evaluate(new GateInput(request.confidence(), request.verifiable()));
        if (decision.action() == ConfidenceGate.Action.PAUSE_FOR_HUMAN) {
            log.info("元引擎调度暂停：置信度不足，等待人工确认 target={}", request.target());
            return new ExecutionResult(false, null, decision.message());
        }

        log.info("元引擎调度: target={} id={}", request.target(), request.targetId());

        return switch (request.target()) {
            case AGENT -> dispatchAgent(request);
            case ASSISTANT -> dispatchAssistant(request);
            case LLM -> dispatchLlm(request);
        };
    }

    private ExecutionResult dispatchAgent(ExecutionRequest request) {
        var definition = agentRegistry.findById(request.targetId());
        if (definition == null) {
            return new ExecutionResult(false, null, "Agent 不存在: " + request.targetId());
        }
        var executor = agentPool.borrow(definition);
        try {
            var result = executor.execute(request.input());
            return new ExecutionResult(result.success(), result.output(), result.error());
        } finally {
            agentPool.release(request.targetId(), executor);
        }
    }

    private ExecutionResult dispatchAssistant(ExecutionRequest request) {
        var sessionId =
                request.sessionId() != null
                        ? request.sessionId()
                        : "dispatch-" + System.currentTimeMillis();
        var response =
                assistantExecutor.chat(
                        sessionId, request.targetId(), request.userId(), request.input());
        return new ExecutionResult(response.success(), response.content(), response.error());
    }

    private ExecutionResult dispatchLlm(ExecutionRequest request) {
        var messages =
                request.systemPrompt() != null
                        ? List.of(
                                LlmMessage.system(request.systemPrompt()),
                                LlmMessage.user(request.input()))
                        : List.of(LlmMessage.user(request.input()));
        var output = llmClient.call(messages, request.targetId(), request.userId());
        return new ExecutionResult(true, output, null);
    }
}
