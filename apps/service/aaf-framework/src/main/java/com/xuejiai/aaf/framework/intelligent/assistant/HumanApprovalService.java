package com.xuejiai.aaf.framework.intelligent.assistant;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Human-in-the-Loop 通用机制——AI 执行中需要人工介入时的统一处理。
 *
 * <p>应用场景：
 *
 * <ul>
 *   <li>工具权限申请（高风险工具需用户确认）
 *   <li>置信度门控（低置信度结果需人工审核）
 *   <li>价值观审核（敏感内容需人工判断）
 *   <li>数据变更确认（不可逆操作需确认）
 * </ul>
 *
 * <p>流程：
 *
 * <pre>
 * AI 执行 → request(type, context) → 返回 requestId
 *   → 通知用户（WebSocket/SSE 推送）
 *   → 用户响应 → resolve(requestId, decision)
 *   → AI 继续执行（或取消）
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HumanApprovalService {

    private final Map<String, ApprovalRequest> pending = new ConcurrentHashMap<>();
    private final ObjectProvider<ApprovalRequestPublisher> publishers;
    private final ApplicationEventPublisher eventPublisher;

    /** 审批类型 */
    public enum ApprovalType {
        TOOL_PERMISSION,
        ACTION_CONFIRM,
        LOW_CONFIDENCE,
        CONTENT_REVIEW,
        CREDIT_RECOVERY,
        VALUE_REVIEW,
        DATA_MUTATION,
        CUSTOM
    }

    /** 用户决策 */
    public enum Decision {
        APPROVED,
        REJECTED,
        TIMEOUT
    }

    /** 审批请求 */
    public record ApprovalRequest(
            String requestId,
            String sessionId,
            Long userId,
            ApprovalType type,
            String title,
            String description,
            String subjectType,
            String subjectKey,
            String riskLevel,
            Double confidence,
            GrantScope grantScope,
            Map<String, Object> context,
            Instant createdAt,
            Duration timeout) {}

    /** 审批通过后可授予的恢复范围。 */
    public enum GrantScope {
        NONE,
        ONCE,
        SESSION,
        PATTERN
    }

    /** 审批结果 */
    public record ApprovalResult(Decision decision, String reason) {
        public static ApprovalResult approved() {
            return new ApprovalResult(Decision.APPROVED, null);
        }

        public static ApprovalResult rejected(String reason) {
            return new ApprovalResult(Decision.REJECTED, reason);
        }

        public static ApprovalResult timeout() {
            return new ApprovalResult(Decision.TIMEOUT, "审批超时");
        }
    }

    /** 审批完成事件，供内容审查、会话授权等模块建立恢复状态。 */
    public record ApprovalResolvedEvent(ApprovalRequest request, ApprovalResult result) {}

    /**
     * 发起审批请求。
     *
     * @return requestId
     */
    public String request(
            String sessionId,
            Long userId,
            ApprovalType type,
            String title,
            String description,
            Map<String, Object> context) {
        var requestId = UUID.randomUUID().toString();
        var request =
                new ApprovalRequest(
                        requestId,
                        sessionId,
                        userId,
                        type,
                        title,
                        description,
                        stringContext(context, "subjectType"),
                        stringContext(context, "subjectKey"),
                        stringContext(context, "riskLevel"),
                        doubleContext(context, "confidence"),
                        grantScopeContext(context),
                        context,
                        Instant.now(),
                        Duration.ofMinutes(5));
        pending.put(requestId, request);
        log.info("HITL 审批请求: id={}, type={}, title={}", requestId, type, title);
        publishers.orderedStream().forEach(publisher -> publisher.publish(request));
        return requestId;
    }

    /** 用户响应审批。 */
    public void resolve(String requestId, Long userId, Decision decision, String reason) {
        var request = pending.get(requestId);
        if (request == null) {
            log.warn("审批请求不存在或已过期: {}", requestId);
            return;
        }
        if (!request.userId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权处理该审批请求");
        }
        pending.remove(requestId);
        log.info("HITL 审批响应: id={}, decision={}", requestId, decision);
        // 结果存入缓存供 AI 轮询获取
        var result = new ApprovalResult(decision, reason);
        results.put(requestId, result);
        eventPublisher.publishEvent(new ApprovalResolvedEvent(request, result));
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
        return pending.values().stream().filter(r -> userId.equals(r.userId())).toList();
    }

    private final Map<String, ApprovalResult> results = new ConcurrentHashMap<>();

    private static String stringContext(Map<String, Object> context, String key) {
        if (context == null) {
            return null;
        }
        var value = context.get(key);
        return value == null ? null : value.toString();
    }

    private static Double doubleContext(Map<String, Object> context, String key) {
        if (context == null) {
            return null;
        }
        var value = context.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static GrantScope grantScopeContext(Map<String, Object> context) {
        var value = stringContext(context, "grantScope");
        if (value == null || value.isBlank()) {
            return GrantScope.NONE;
        }
        try {
            return GrantScope.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return GrantScope.NONE;
        }
    }
}
