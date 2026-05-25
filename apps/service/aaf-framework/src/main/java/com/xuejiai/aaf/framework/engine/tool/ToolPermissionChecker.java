package com.xuejiai.aaf.framework.engine.tool;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.assistant.HumanApprovalService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具权限检查器——参考 Kiro CLI 权限模型。
 *
 * <p>决策优先级：
 * <ol>
 *   <li>deny 黑名单 → DENIED</li>
 *   <li>grantAll → GRANTED</li>
 *   <li>trust 白名单（会话级，含 pattern 匹配） → GRANTED</li>
 *   <li>allowedTools（Agent 级） → GRANTED</li>
 *   <li>readOnly 工具 → AUTO_GRANTED</li>
 *   <li>风险等级评估 → 按等级决策</li>
 * </ol>
 *
 * <p>防重复：用户确认后按 GrantScope 记忆，后续调用（含失败重试）自动通过。
 * <p>持久化：授权状态通过 {@link ToolPermissionStateChanged} 事件通知 SessionManager 持久化到 Redis。
 * <p>审计：每次权限决策发布 {@link ToolPermissionDecisionEvent} 记录到执行追踪。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolPermissionChecker {

    private final HumanApprovalService approvalService;
    private final ApplicationEventPublisher eventPublisher;

    /** 会话级授权记录：sessionId → 授权列表 */
    private final Map<String, List<TrustGrant>> grants = new ConcurrentHashMap<>();
    /** 会话级黑名单：sessionId → 被禁工具名 */
    private final Map<String, Set<String>> denied = new ConcurrentHashMap<>();
    /** 会话级全部信任标记 */
    private final Set<String> trustAllSessions = ConcurrentHashMap.newKeySet();

    /** 权限检查结果 */
    public enum PermissionResult {
        GRANTED,
        AUTO_GRANTED,
        PENDING_APPROVAL,
        DENIED
    }

    /** 授权范围 */
    public enum GrantScope {
        /** 仅本次调用（一次性，用完即失效） */
        ONCE,
        /** 本次会话内同工具自动通过 */
        SESSION,
        /** 本次会话内匹配参数模式的调用自动通过（如 "path:/tmp/**"） */
        PATTERN
    }

    /** 授权记录（可序列化，支持持久化到 Redis） */
    public record TrustGrant(
            String toolName,
            GrantScope scope,
            String pattern,
            Instant grantedAt,
            boolean consumed
    ) {
        public static TrustGrant session(String toolName) {
            return new TrustGrant(toolName, GrantScope.SESSION, null, Instant.now(), false);
        }

        public static TrustGrant pattern(String toolName, String pattern) {
            return new TrustGrant(toolName, GrantScope.PATTERN, pattern, Instant.now(), false);
        }

        public static TrustGrant once(String toolName) {
            return new TrustGrant(toolName, GrantScope.ONCE, null, Instant.now(), false);
        }

        TrustGrant markConsumed() {
            return new TrustGrant(toolName, scope, pattern, grantedAt, true);
        }
    }

    /** 权限状态变更事件——通知 SessionManager 持久化。 */
    public record ToolPermissionStateChanged(
            String sessionId,
            List<TrustGrant> grants,
            Set<String> denied,
            boolean trustAll) {}

    /** 权限决策日志事件——记录到执行追踪。 */
    public record ToolPermissionDecisionEvent(
            String sessionId,
            String toolName,
            PermissionResult result,
            String reason,
            Instant decidedAt) {}

    /**
     * 检查工具调用权限（含参数，用于 pattern 匹配）。
     */
    public PermissionResult check(
            String sessionId, Long userId, String toolName,
            ToolRiskLevel riskLevel, boolean readOnly,
            List<String> agentAllowedTools, String arguments) {

        PermissionResult result;
        String reason;

        // 1. deny 黑名单优先
        if (isDenied(sessionId, toolName)) {
            result = PermissionResult.DENIED;
            reason = "黑名单";
        }
        // 2. trust-all
        else if (trustAllSessions.contains(sessionId)) {
            result = PermissionResult.GRANTED;
            reason = "trust-all";
        }
        // 3. 会话级 trust（含 pattern 匹配 + 防重复）
        else if (matchesGrant(sessionId, toolName, arguments)) {
            result = PermissionResult.GRANTED;
            reason = "已授权（防重复）";
        }
        // 4. Agent 级 allowedTools
        else if (agentAllowedTools != null && agentAllowedTools.contains(toolName)) {
            result = PermissionResult.GRANTED;
            reason = "Agent allowedTools";
        }
        // 5. readOnly 自动通过
        else if (readOnly) {
            result = PermissionResult.AUTO_GRANTED;
            reason = "只读工具";
        }
        // 6. 按风险等级决策
        else {
            result = evaluateByRisk(sessionId, userId, toolName, riskLevel);
            reason = "风险评估: " + riskLevel;
        }

        // 发布决策日志事件
        publishDecision(sessionId, toolName, result, reason);
        return result;
    }

    /** 无参数版本。 */
    public PermissionResult check(
            String sessionId, Long userId, String toolName,
            ToolRiskLevel riskLevel, boolean readOnly, List<String> agentAllowedTools) {
        return check(sessionId, userId, toolName, riskLevel, readOnly, agentAllowedTools, null);
    }

    /** 兼容旧接口。 */
    public PermissionResult check(
            String sessionId, Long userId, String toolName,
            ToolRiskLevel riskLevel, boolean hasRolePermission) {
        if (hasRolePermission) return PermissionResult.GRANTED;
        return check(sessionId, userId, toolName, riskLevel, false, null, null);
    }

    private PermissionResult evaluateByRisk(
            String sessionId, Long userId, String toolName, ToolRiskLevel riskLevel) {
        return switch (riskLevel) {
            case NONE, LOW -> {
                log.debug("工具自动授权: {} (risk={})", toolName, riskLevel);
                yield PermissionResult.AUTO_GRANTED;
            }
            case MEDIUM -> {
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

    // ========== 授权 API ==========

    /** 信任所有工具。 */
    public void grantAll(String sessionId) {
        trustAllSessions.add(sessionId);
        publishStateChanged(sessionId);
        log.info("会话全部信任: session={}", sessionId);
    }

    /** 批量信任指定工具（会话级）。 */
    public void grantBatch(String sessionId, List<String> toolNames) {
        var list = grants.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()));
        toolNames.forEach(name -> list.add(TrustGrant.session(name)));
        publishStateChanged(sessionId);
        log.info("批量信任: session={}, tools={}", sessionId, toolNames);
    }

    /** 信任单个工具（会话级）。 */
    public void grant(String sessionId, String toolName) {
        grantWithScope(sessionId, toolName, GrantScope.SESSION, null);
    }

    /** 带范围的授权。 */
    public void grantWithScope(String sessionId, String toolName, GrantScope scope, String pattern) {
        var list = grants.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()));
        var grant = switch (scope) {
            case ONCE -> TrustGrant.once(toolName);
            case SESSION -> TrustGrant.session(toolName);
            case PATTERN -> TrustGrant.pattern(toolName, pattern);
        };
        list.add(grant);
        publishStateChanged(sessionId);
        log.info("授权工具: session={}, tool={}, scope={}, pattern={}", sessionId, toolName, scope, pattern);
    }

    /** 撤回信任。 */
    public void revoke(String sessionId, String toolName) {
        var list = grants.get(sessionId);
        if (list != null) list.removeIf(g -> g.toolName().equals(toolName));
        publishStateChanged(sessionId);
    }

    /** 加入黑名单。 */
    public void deny(String sessionId, String toolName) {
        denied.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(toolName);
        publishStateChanged(sessionId);
    }

    /** 批量加入黑名单。 */
    public void denyBatch(String sessionId, List<String> toolNames) {
        denied.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).addAll(toolNames);
        publishStateChanged(sessionId);
    }

    /** 重置为默认。 */
    public void resetSession(String sessionId) {
        grants.remove(sessionId);
        denied.remove(sessionId);
        trustAllSessions.remove(sessionId);
        publishStateChanged(sessionId);
    }

    /** 会话结束时清理。 */
    public void clearSession(String sessionId) {
        resetSession(sessionId);
    }

    // ========== 状态恢复（从 SessionManager 加载） ==========

    /** 从持久化状态恢复授权记录（会话恢复时调用）。 */
    public void restoreState(String sessionId, List<TrustGrant> restoredGrants,
                             Set<String> restoredDenied, boolean restoredTrustAll) {
        if (restoredGrants != null && !restoredGrants.isEmpty()) {
            grants.put(sessionId, Collections.synchronizedList(new ArrayList<>(restoredGrants)));
        }
        if (restoredDenied != null && !restoredDenied.isEmpty()) {
            denied.put(sessionId, ConcurrentHashMap.newKeySet());
            denied.get(sessionId).addAll(restoredDenied);
        }
        if (restoredTrustAll) {
            trustAllSessions.add(sessionId);
        }
        log.info("权限状态已恢复: session={}, grants={}, denied={}, trustAll={}",
                sessionId,
                restoredGrants != null ? restoredGrants.size() : 0,
                restoredDenied != null ? restoredDenied.size() : 0,
                restoredTrustAll);
    }

    // ========== 内部方法 ==========

    private boolean matchesGrant(String sessionId, String toolName, String arguments) {
        var list = grants.get(sessionId);
        if (list == null) return false;

        for (int i = 0; i < list.size(); i++) {
            var g = list.get(i);
            if (!g.toolName().equals(toolName)) continue;

            switch (g.scope()) {
                case SESSION -> { return true; }
                case ONCE -> {
                    if (!g.consumed()) {
                        list.set(i, g.markConsumed());
                        return true;
                    }
                }
                case PATTERN -> {
                    if (g.pattern() != null && matchesPattern(g.pattern(), arguments)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean matchesPattern(String pattern, String arguments) {
        if (arguments == null || arguments.isBlank()) return false;

        var parts = pattern.split(":", 2);
        if (parts.length < 2) {
            return arguments.contains(parts[0]);
        }

        var key = parts[0];
        var glob = parts[1];

        if (!arguments.contains(key)) return false;

        var regex = glob
                .replace(".", "\\.")
                .replace("**", "§§")
                .replace("*", "[^/]*")
                .replace("§§", ".*")
                .replace("?", ".");

        return arguments.matches(".*" + regex + ".*");
    }

    private boolean isDenied(String sessionId, String toolName) {
        var set = denied.get(sessionId);
        return set != null && set.contains(toolName);
    }

    private void publishStateChanged(String sessionId) {
        var sessionGrants = grants.getOrDefault(sessionId, List.of());
        var sessionDenied = denied.getOrDefault(sessionId, Set.of());
        var trustAll = trustAllSessions.contains(sessionId);
        eventPublisher.publishEvent(
                new ToolPermissionStateChanged(sessionId, List.copyOf(sessionGrants), Set.copyOf(sessionDenied), trustAll));
    }

    private void publishDecision(String sessionId, String toolName, PermissionResult result, String reason) {
        eventPublisher.publishEvent(
                new ToolPermissionDecisionEvent(sessionId, toolName, result, reason, Instant.now()));
    }

    /** @deprecated 使用 grant 替代 */
    @Deprecated
    public void grantTemporary(String sessionId, String toolName) {
        grant(sessionId, toolName);
    }
}
