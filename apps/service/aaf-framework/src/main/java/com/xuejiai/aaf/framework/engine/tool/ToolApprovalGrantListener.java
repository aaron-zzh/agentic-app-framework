package com.xuejiai.aaf.framework.engine.tool;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.assistant.hitl.ToolApprovalService;
import com.xuejiai.aaf.framework.intelligent.assistant.hitl.ToolApprovalService.ApprovalResolvedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具审批通过后回写会话级授权（M36）。
 *
 * <p>补齐旧链缺失的闭环：内存版 HITL 只创建审批、没有任何消费方，用户即使在渠道卡片上点了"同意"， 也没有代码把这个决定变成工具授权，Agent 重试仍会再次被拦。
 *
 * <p>现在审批持久化后，决定事件在这里落为 {@link ToolPermissionChecker} 的会话级授权：
 *
 * <ul>
 *   <li>APPROVED + grantScope=SESSION/ONCE/PATTERN → 授予对应范围
 *   <li>REJECTED → 加入会话黑名单，避免同一工具反复弹确认
 *   <li>TIMEOUT / grantScope=NONE → 不改变授权状态
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolApprovalGrantListener {

    private final ToolPermissionChecker permissionChecker;

    @EventListener
    public void onApprovalResolved(ApprovalResolvedEvent event) {
        var request = event.request();
        var sessionId = request.sessionId();
        var toolName = request.subjectKey();
        if (sessionId == null || toolName == null || toolName.isBlank()) {
            // 无会话作用域（REST 直调）或非工具类审批（如内容审查）无需回写工具授权
            return;
        }
        switch (event.result().decision()) {
            case APPROVED -> grant(sessionId, toolName, request.grantScope());
            case REJECTED -> {
                permissionChecker.deny(sessionId, toolName);
                log.info("审批拒绝，工具加入会话黑名单: session={}, tool={}", sessionId, toolName);
            }
            case TIMEOUT -> log.info("审批超时，不变更授权: session={}, tool={}", sessionId, toolName);
        }
    }

    private void grant(String sessionId, String toolName, ToolApprovalService.GrantScope scope) {
        if (scope == null || scope == ToolApprovalService.GrantScope.NONE) {
            return;
        }
        var mapped =
                switch (scope) {
                    case ONCE -> ToolPermissionChecker.GrantScope.ONCE;
                    case PATTERN -> ToolPermissionChecker.GrantScope.PATTERN;
                    default -> ToolPermissionChecker.GrantScope.SESSION;
                };
        permissionChecker.grantWithScope(sessionId, toolName, mapped, null);
        log.info("审批通过，已授予工具权限: session={}, tool={}, scope={}", sessionId, toolName, mapped);
    }
}
