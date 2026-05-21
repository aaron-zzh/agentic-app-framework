package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import io.agentscope.core.session.Session;
import io.agentscope.core.session.SessionManager;
import io.agentscope.core.state.StateModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope Session 适配器。
 *
 * <p>封装 AgentScope {@link SessionManager} 的 Builder 风格 API，提供简化的会话管理。
 *
 * <p>AgentScope 提供多种 Session 后端：
 *
 * <ul>
 *   <li>{@code InMemorySession} — 开发/测试
 *   <li>{@code agentscope-extensions-session-redis} — 生产（Redis 持久化）
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class AgentScopeSessionAdapter {

    private final Session session;

    /** 加载会话状态（如存在）。 */
    public void loadIfExists(String sessionId, StateModule... components) {
        var manager = SessionManager.forSessionId(sessionId).withSession(session);
        for (var component : components) {
            manager.addComponent(component);
        }
        manager.loadIfExists();
    }

    /** 保存会话状态。 */
    public void save(String sessionId, StateModule... components) {
        var manager = SessionManager.forSessionId(sessionId).withSession(session);
        for (var component : components) {
            manager.addComponent(component);
        }
        manager.saveSession();
    }

    /** 删除会话。 */
    public void remove(String sessionId) {
        session.delete(io.agentscope.core.state.SimpleSessionKey.of(sessionId));
    }
}
