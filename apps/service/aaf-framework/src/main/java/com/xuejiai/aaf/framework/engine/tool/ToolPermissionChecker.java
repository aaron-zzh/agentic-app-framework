package com.xuejiai.aaf.framework.engine.tool;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.assistant.HumanApprovalService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具权限检查器——基于通用 HITL 机制实现。
 *
 * <p>高风险工具调用时，通过 {@link HumanApprovalService} 发起审批请求。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolPermissionChecker {

    private final HumanApprovalService approvalService;

    /** 临时授权缓存：sessionId:toolName → 已授权 */
    private final Map<String, Set<String>> tempGrants = new ConcurrentHashMap<>();

    /** 权限检查结果 */
    public enum PermissionResult {
        /** 有权限，直接执行 */
        GRANTED,
        /** 自动授权（低风险，记录日志） */
        AUTO_GRANTED,
        /** 等待用户确认 */
        PENDING_APPROVAL,
        /** 拒绝（需管理员审批） */
        DENIED
    }

    /**
     * 检查工具调用权限。
     *
     * @param sessionId 当前会话
     * @param userId 用户 ID
     * @param toolName 工具名
     * @param riskLevel 工具风险等级
     * @param hasRolePermission 是否在 Role 白名单中
     * @return 权限结果
     */
    public PermissionResult check(String sessionId, Long userId, String toolName, ToolRiskLevel riskLevel, boolean hasRolePermission) {
        if (hasRolePermission) return PermissionResult.GRANTED;

        return switch (riskLevel) {
            case NONE, LOW -> {
                log.debug("工具自动授权: {} (risk={})", toolName, riskLevel);
                yield PermissionResult.AUTO_GRANTED;
            }
            case MEDIUM -> {
                var grants = tempGrants.getOrDefault(sessionId, Set.of());
                if (grants.contains(toolName)) {
                    yield PermissionResult.GRANTED;
                }
                // 发起 HITL 审批
                approvalService.request(sessionId, userId,
                        HumanApprovalService.ApprovalType.TOOL_PERMISSION,
                        "工具调用确认",
                        "Agent 请求调用工具 [%s]（风险等级: %s）".formatted(toolName, riskLevel),
                        Map.of("toolName", toolName, "riskLevel", riskLevel.name()));
                yield PermissionResult.PENDING_APPROVAL;
            }
            case HIGH -> {
                approvalService.request(sessionId, userId,
                        HumanApprovalService.ApprovalType.TOOL_PERMISSION,
                        "高风险工具确认",
                        "Agent 请求调用高风险工具 [%s]，每次调用需确认".formatted(toolName),
                        Map.of("toolName", toolName, "riskLevel", riskLevel.name()));
                yield PermissionResult.PENDING_APPROVAL;
            }
            case CRITICAL -> PermissionResult.DENIED;
        };
    }

    /** 用户批准后，授予临时权限（本次会话有效）。 */
    public void grantTemporary(String sessionId, String toolName) {
        tempGrants.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(toolName);
        log.info("临时授权: session={}, tool={}", sessionId, toolName);
    }

    /** 会话结束时清理临时授权。 */
    public void clearSession(String sessionId) {
        tempGrants.remove(sessionId);
    }
}
