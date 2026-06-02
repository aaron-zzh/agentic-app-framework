package com.xuejiai.aaf.module.ai.agui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuejiai.aaf.framework.intelligent.core.assistant.ChatSessionResolver;
import com.xuejiai.aaf.framework.intelligent.agent.context.AgentRunContextHolder;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agui.AguiException;
import io.agentscope.core.agui.processor.AgentResolver;
import io.agentscope.core.agui.registry.AguiAgentRegistry;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.session.Session;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;

/**
 * AAF 自定义 AgentResolver——在执行线程上解析 Agent 时：
 * <ol>
 *   <li>按 threadId 查会话上下文，设置 {@link AgentRunContextHolder}（供 Hook 读取，解决线程边界）</li>
 *   <li>冷启动时先尝试从 Redis Session 恢复 Agent 状态，失败则从 DB 播种历史</li>
 * </ol>
 */
public class AafAgentResolver implements AgentResolver {

    private static final Logger log = LoggerFactory.getLogger(AafAgentResolver.class);

    private final AguiAgentRegistry registry;
    private final ThreadSessionManager sessionManager;
    private final ChatSessionResolver chatSessionResolver;
    private final Session agentSession;

    public AafAgentResolver(
            AguiAgentRegistry registry,
            ThreadSessionManager sessionManager,
            ChatSessionResolver chatSessionResolver,
            Session agentSession) {
        this.registry = registry;
        this.sessionManager = sessionManager;
        this.chatSessionResolver = chatSessionResolver;
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
        if (ctx != null) {
            AgentRunContextHolder.set(
                    threadId, ctx.userId(), agentId, ctx.assistantId(), threadId, ctx.knowledgeBaseId());
        }

        // 2. 获取/创建 Agent（server-side memory：按 threadId 复用）
        var agent = sessionManager.getOrCreateAgent(
                threadId, agentId,
                () -> {
                    var created = registry.getAgent(agentId)
                            .orElseThrow(() -> new AguiException.AgentNotFoundException(agentId));
                    // 新建 Agent 时从 Redis Session 恢复状态
                    if (created instanceof ReActAgent reactAgent) {
                        reactAgent.loadIfExists(agentSession, threadId);
                        if (reactAgent.getMemory() != null && !reactAgent.getMemory().getMessages().isEmpty()) {
                            log.debug("从 Redis Session 恢复 Agent 状态: threadId={}", threadId);
                            return created;
                        }
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
        var msgRole = switch (role == null ? "user" : role.toLowerCase()) {
            case "assistant" -> MsgRole.ASSISTANT;
            case "system" -> MsgRole.SYSTEM;
            case "tool" -> MsgRole.TOOL;
            default -> MsgRole.USER;
        };
        return Msg.builder()
                .role(msgRole)
                .textContent(content == null ? "" : content)
                .build();
    }
}
