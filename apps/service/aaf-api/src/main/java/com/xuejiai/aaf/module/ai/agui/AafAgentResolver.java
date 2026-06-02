package com.xuejiai.aaf.module.ai.agui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuejiai.aaf.framework.intelligent.agent.context.AgentRunContextHolder;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantRuntime;
import com.xuejiai.aaf.framework.intelligent.core.assistant.AssistantRuntime.MaterializeContext;
import com.xuejiai.aaf.framework.intelligent.core.assistant.ChatSessionResolver;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agui.processor.AgentResolver;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.session.Session;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;

/**
 * AAF 自定义 AgentResolver——AG-UI 入口的 Agent 解析逻辑。
 *
 * <p>核心变更：不再从 Registry 取裸 Agent，改为通过 {@link AssistantRuntime#materialize} 物化协调者。
 *
 * <ol>
 *   <li>按 threadId 查会话上下文（ChatSession），设置 {@link AgentRunContextHolder}
 *   <li>通过 AssistantRuntime.materialize() 构建协调者 Agent（含完整 Hook 链）
 *   <li>ThreadSessionManager 按 threadId 缓存复用，冷启动时从 Redis Session 恢复或 DB 播种历史
 * </ol>
 */
public class AafAgentResolver implements AgentResolver {

    private static final Logger log = LoggerFactory.getLogger(AafAgentResolver.class);

    private final ThreadSessionManager sessionManager;
    private final ChatSessionResolver chatSessionResolver;
    private final AssistantRuntime assistantRuntime;
    private final Session agentSession;

    public AafAgentResolver(
            ThreadSessionManager sessionManager,
            ChatSessionResolver chatSessionResolver,
            AssistantRuntime assistantRuntime,
            Session agentSession) {
        this.sessionManager = sessionManager;
        this.chatSessionResolver = chatSessionResolver;
        this.assistantRuntime = assistantRuntime;
        this.agentSession = agentSession;
    }

    /** 获取 AgentScope Session（供 Controller 在请求完成后 saveTo）。 */
    public Session getAgentSession() {
        return agentSession;
    }

    @Override
    public Agent resolveAgent(String agentId, String threadId) {
        // 1. 解析会话上下文，设置 ThreadLocal（执行线程，先于 agent.stream，Hook 可见）
        var ctx = chatSessionResolver.resolveByThreadId(threadId);
        var assistantId = ctx != null ? ctx.assistantId() : null;
        var userId = ctx != null ? ctx.userId() : null;
        var knowledgeBaseId = ctx != null ? ctx.knowledgeBaseId() : null;

        if (ctx != null) {
            AgentRunContextHolder.set(
                    threadId, userId, agentId, assistantId, threadId, knowledgeBaseId);
        }

        // 2. 获取/创建 Agent（按 threadId 缓存复用）
        var agent =
                sessionManager.getOrCreateAgent(
                        threadId,
                        agentId,
                        () -> {
                            // 通过 AssistantRuntime 物化协调者（统一逻辑）
                            var materialCtx =
                                    new MaterializeContext(
                                            assistantId != null ? assistantId : "default",
                                            userId,
                                            threadId,
                                            knowledgeBaseId);
                            var created = assistantRuntime.materialize(materialCtx);

                            // 从 Redis Session 恢复状态
                            created.loadIfExists(agentSession, threadId);
                            if (created.getMemory() != null
                                    && !created.getMemory().getMessages().isEmpty()) {
                                log.debug("从 Redis Session 恢复 Agent 状态: threadId={}", threadId);
                                return created;
                            }
                            return created;
                        });

        // 3. 兜底：Redis 无状态时从 DB 播种历史
        if (ctx != null && ctx.sessionId() != null && agent instanceof ReActAgent reactAgent) {
            var memory = reactAgent.getMemory();
            if (memory != null && memory.getMessages().isEmpty()) {
                var history = chatSessionResolver.loadHistory(ctx.sessionId());
                for (var h : history) {
                    memory.addMessage(toMsg(h.role(), h.content()));
                }
                if (!history.isEmpty()) {
                    log.debug("从 DB 播种历史: threadId={}, 条数={}", threadId, history.size());
                }
            }
        }
        return agent;
    }

    @Override
    public boolean hasMemory(String threadId) {
        return sessionManager.hasMemory(threadId);
    }

    private Msg toMsg(String role, String content) {
        var msgRole =
                switch (role == null ? "user" : role.toLowerCase()) {
                    case "assistant" -> MsgRole.ASSISTANT;
                    case "system" -> MsgRole.SYSTEM;
                    case "tool" -> MsgRole.TOOL;
                    default -> MsgRole.USER;
                };
        return Msg.builder().role(msgRole).textContent(content == null ? "" : content).build();
    }
}
