package com.xuejiai.aaf.framework.intelligent.assistant;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Human-in-the-Loop 通用机制——AI 执行中需要人工介入时的统一处理。
 *
 * <p>应用场景：
 * <ul>
 *   <li>工具权限申请（高风险工具需用户确认）</li>
 *   <li>置信度门控（低置信度结果需人工审核）</li>
 *   <li>价值观审核（敏感内容需人工判断）</li>
 *   <li>数据变更确认（不可逆操作需确认）</li>
 * </ul>
 *
 * <p>流程：
 * <pre>
 * AI 执行 → request(type, context) → 返回 requestId
 *   → 通知用户（WebSocket/SSE 推送）
 *   → 用户响应 → resolve(requestId, decision)
 *   → AI 继续执行（或取消）
 * </pre>
 */
@Slf4j
@Component
public class HumanApprovalService {

    private final Map<String, ApprovalRequest> pending = new ConcurrentHashMap<>();

    /** 审批类型 */
    public enum ApprovalType {
        TOOL_PERMISSION,
        LOW_CONFIDENCE,
        VALUE_REVIEW,
        DATA_MUTATION,
        CUSTOM
    }

    /** 用户决策 */
    public enum Decision { APPROVED, REJECTED, TIMEOUT }

    /** 审批请求 */
    public record ApprovalRequest(
            String requestId,
            String sessionId,
            Long userId,
            ApprovalType type,
            String title,
            String description,
            Map<String, Object> context,
            Instant createdAt,
            Duration timeout) {}

    /** 审批结果 */
    public record ApprovalResult(Decision decision, String reason) {
        public static ApprovalResult approved() { return new ApprovalResult(Decision.APPROVED, null); }
        public static ApprovalResult rejected(String reason) { return new ApprovalResult(Decision.REJECTED, reason); }
        public static ApprovalResult timeout() { return new ApprovalResult(Decision.TIMEOUT, "审批超时"); }
    }

    /**
     * 发起审批请求。
     *
     * @return requestId
     */
    public String request(String sessionId, Long userId, ApprovalType type, String title, String description, Map<String, Object> context) {
        var requestId = sessionId + ":" + System.nanoTime();
        var request = new ApprovalRequest(
                requestId, sessionId, userId, type, title, description,
                context, Instant.now(), Duration.ofMinutes(5));
        pending.put(requestId, request);
        log.info("HITL 审批请求: id={}, type={}, title={}", requestId, type, title);
        // TODO: 通过 WebSocket/SSE 推送给用户
        return requestId;
    }

    /**
     * 用户响应审批。
     */
    public void resolve(String requestId, Decision decision, String reason) {
        var request = pending.remove(requestId);
        if (request == null) {
            log.warn("审批请求不存在或已过期: {}", requestId);
            return;
        }
        log.info("HITL 审批响应: id={}, decision={}", requestId, decision);
        // 结果存入缓存供 AI 轮询获取
        results.put(requestId, new ApprovalResult(decision, reason));
    }

    /** 查询审批结果（AI 侧轮询）。 */
    public Optional<ApprovalResult> getResult(String requestId) {
        // 检查超时
        var request = pending.get(requestId);
        if (request != null && Instant.now().isAfter(request.createdAt().plus(request.timeout()))) {
            pending.remove(requestId);
            return Optional.of(ApprovalResult.timeout());
        }
        return Optional.ofNullable(results.remove(requestId));
    }

    /** 查询用户待处理的审批列表。 */
    public java.util.List<ApprovalRequest> getPending(Long userId) {
        return pending.values().stream()
                .filter(r -> userId.equals(r.userId()))
                .toList();
    }

    private final Map<String, ApprovalResult> results = new ConcurrentHashMap<>();
}
