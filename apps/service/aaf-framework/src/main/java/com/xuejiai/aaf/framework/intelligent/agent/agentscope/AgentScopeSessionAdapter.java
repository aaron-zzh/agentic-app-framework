package com.xuejiai.aaf.framework.intelligent.agent.agentscope;

import io.agentscope.core.session.Session;
import io.agentscope.core.session.SessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope Session → AAF SessionManager 适配器。
 *
 * <p>适配策略：委托给 AgentScope {@link SessionManager}，
 * 替换 AAF 自研的 {@code com.xuejiai.aaf.framework.intelligent.assistant.SessionManager}。
 *
 * <p>AgentScope 提供多种 Session 后端：
 * <ul>
 *   <li>{@code InMemorySession} — 开发/测试</li>
 *   <li>{@code agentscope-extensions-session-redis} — 生产（Redis 持久化）</li>
 *   <li>{@code agentscope-extensions-session-mysql} — 生产（MySQL 持久化）</li>
 * </ul>
 * 通过 Spring Boot Starter 自动配置，无需手动切换。
 *
 * <p>TODO: 引入 agentscope-spring-boot-starter 后，注入 AgentScope SessionManager Bean，
 * 替换 AAF 自研 SessionManager 的注入点。
 */
@Slf4j
@RequiredArgsConstructor
public class AgentScopeSessionAdapter {

    private final SessionManager agentScopeSessionManager;

    /**
     * 获取或创建会话。
     *
     * @param sessionId 会话 ID
     * @return AgentScope Session
     */
    public Session getOrCreate(String sessionId) {
        return agentScopeSessionManager.getOrCreate(sessionId);
    }

    /**
     * 删除会话（用户登出/超时清理）。
     */
    public void remove(String sessionId) {
        agentScopeSessionManager.remove(sessionId);
    }
}
