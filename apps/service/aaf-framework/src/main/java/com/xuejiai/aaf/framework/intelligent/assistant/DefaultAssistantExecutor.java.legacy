package com.xuejiai.aaf.framework.intelligent.assistant;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.agent.context.AgentRunContextHolder;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryMessage;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.ShortTermMemoryService;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantExecutor;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantRuntime;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantRuntime.MaterializeContext;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AssistantExecutor 默认实现——渠道/A2A/内部调度入口（完整输出模式）。
 *
 * <p>与 AG-UI 入口共享同一个 {@link AssistantRuntime#materialize} 逻辑， 差异仅在输出方式：本类使用 {@code agent.call(msg)}
 * 同步等待完整结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAssistantExecutor implements AssistantExecutor {

    private final AssistantRuntime assistantRuntime;
    private final ShortTermMemoryService shortTermMemory;
    private final SessionManager sessionManager;

    @Override
    public AssistantResponse chat(
            String sessionId, String assistantId, Long userId, String userMessage) {
        // 设置运行上下文（供 Hook 读取）
        AgentRunContextHolder.set(sessionId, userId, "assistant", assistantId, sessionId, null);
        try {
            // 会话管理：确保会话存在并更新状态
            sessionManager
                    .getSession(sessionId)
                    .orElseGet(() -> sessionManager.createSession(userId, assistantId));
            sessionManager.updateStatus(sessionId, SessionManager.SessionStatus.PROCESSING);

            // 物化协调者 Agent（与 AG-UI 入口共享同一逻辑）
            var ctx = new MaterializeContext(assistantId, userId, sessionId, null);
            var agent = assistantRuntime.materialize(ctx);

            // 记录用户消息到短期记忆
            shortTermMemory.append(sessionId, new MemoryMessage("user", userMessage, null));

            // 同步执行（完整输出）
            var msg =
                    Msg.builder().name("user").role(MsgRole.USER).textContent(userMessage).build();
            var response = agent.call(msg).block();

            var content = response != null ? response.getTextContent() : "";
            if (content == null) content = "";

            // 记录响应到短期记忆
            shortTermMemory.append(sessionId, new MemoryMessage("assistant", content, null));
            sessionManager.updateStatus(sessionId, SessionManager.SessionStatus.ACTIVE);

            return AssistantResponse.success(content, sessionId);
        } catch (Exception e) {
            log.warn(
                    "助理执行失败 [assistant={}, session={}]: {}",
                    assistantId,
                    sessionId,
                    e.getMessage());
            sessionManager.updateStatus(sessionId, SessionManager.SessionStatus.ACTIVE);
            return AssistantResponse.error(sessionId, e.getMessage());
        } finally {
            AgentRunContextHolder.clear();
        }
    }
}
