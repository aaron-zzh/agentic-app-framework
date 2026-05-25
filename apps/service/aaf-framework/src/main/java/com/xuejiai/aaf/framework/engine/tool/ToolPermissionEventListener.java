package com.xuejiai.aaf.framework.engine.tool;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.tool.ToolPermissionChecker.ToolPermissionDecisionEvent;
import com.xuejiai.aaf.framework.engine.tool.ToolPermissionChecker.ToolPermissionStateChanged;
import com.xuejiai.aaf.framework.intelligent.assistant.SessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具权限事件监听器：
 * <ul>
 *   <li>状态变更 → 持久化到 SessionManager（Redis），支持会话恢复</li>
 *   <li>决策日志 → 记录审计日志（与执行追踪关联）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolPermissionEventListener {

    private final SessionManager sessionManager;

    /** 授权状态变更 → 持久化到会话 context。 */
    @EventListener
    public void onStateChanged(ToolPermissionStateChanged event) {
        sessionManager.getSession(event.sessionId()).ifPresent(session -> {
            session.getContext().put("tool_grants", event.grants());
            session.getContext().put("tool_denied", event.denied());
            session.getContext().put("tool_trust_all", event.trustAll());
        });
    }

    /** 权限决策 → 审计日志。 */
    @EventListener
    public void onDecision(ToolPermissionDecisionEvent event) {
        if (event.result() == ToolPermissionChecker.PermissionResult.DENIED
                || event.result() == ToolPermissionChecker.PermissionResult.PENDING_APPROVAL) {
            log.info("[权限审计] session={} tool={} result={} reason={}",
                    event.sessionId(), event.toolName(), event.result(), event.reason());
        } else {
            log.debug("[权限审计] session={} tool={} result={} reason={}",
                    event.sessionId(), event.toolName(), event.result(), event.reason());
        }
    }
}
