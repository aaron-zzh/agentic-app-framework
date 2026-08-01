package com.xuejiai.aaf.framework.intelligent.assistant.hitl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具层人工确认服务（M36：替代原内存版 {@code HumanApprovalService}）。
 *
 * <p>修复的问题：原实现用 {@code ConcurrentHashMap} 存 pending/results，
 *
 * <ul>
 *   <li>重启或多实例部署即丢失待审批状态
 *   <li>全代码库没有任何 {@code resolve/getResult/getPending} 调用方——审批建出来后**无人可处理**， 而 publisher 已把卡片推给用户，用户点了也无处落地
 * </ul>
 *
 * <p>现在状态落 {@code ai_tool_approval}，并提供可用的决定入口（{@code ToolApprovalController}）。 批准后由 {@code
 * ToolApprovalGrantListener} 消费 {@link ApprovalResolvedEvent} 回写会话级工具授权。
 *
 * <p>与任务级 HITL 的边界：任务级审批（有 AssistantTask/InvocationContext，决定后要迁移任务状态并恢复执行）
 * 走 {@code PersistentHitlCoordinator} + {@code ai_hitl_approval}；本服务面向 {@code ToolService} REST 调用与
 * Flowable {@code ToolNode} 这类**没有任务上下文**的工具确认。两者都持久化，职责不重叠。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolApprovalService {

    /** 无会话场景的作用域占位（REST 直调工具时 sessionId 为 null） */
    private static final String NO_SCOPE = "-";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private final ToolApprovalRepository repository;
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

    /** 审批通过后可授予的恢复范围。 */
    public enum GrantScope {
        NONE,
        ONCE,
        SESSION,
        PATTERN
    }

    /** 审批请求（对外视图，字段与原内存版保持一致，调用方无需改造） */
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

    /** 审批完成事件，供内容审查、会话工具授权等模块建立恢复状态。 */
    public record ApprovalResolvedEvent(ApprovalRequest request, ApprovalResult result) {}

    /**
     * 发起审批请求。
     *
     * @return requestId（持久化后的 approvalId）
     */
    @Transactional
    public String request(
            String sessionId,
            Long userId,
            ApprovalType type,
            String title,
            String description,
            Map<String, Object> context) {
        var now = Instant.now();
        var entity = new ToolApprovalEntity();
        entity.setApprovalId(UUID.randomUUID().toString());
        entity.setScopeKey(sessionId == null || sessionId.isBlank() ? NO_SCOPE : sessionId);
        entity.setUserId(userId);
        entity.setApprovalType(type.name());
        entity.setTitle(title);
        entity.setDescription(description);
        entity.setSubjectType(stringContext(context, "subjectType"));
        entity.setSubjectKey(stringContext(context, "subjectKey"));
        entity.setRiskLevel(stringContext(context, "riskLevel"));
        entity.setConfidence(doubleContext(context, "confidence"));
        entity.setGrantScope(grantScopeContext(context).name());
        entity.setContextJson(context == null ? null : JsonUtils.toJsonString(context));
        entity.setStatus("PENDING");
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plus(DEFAULT_TIMEOUT));
        repository.save(entity);

        var request = toRequest(entity, context);
        log.info("工具审批请求已持久化: id={}, type={}, title={}", entity.getApprovalId(), type, title);
        publishers.orderedStream().forEach(publisher -> publisher.publish(request));
        return entity.getApprovalId();
    }

    /** 用户响应审批。 */
    @Transactional
    public void decide(String approvalId, Long userId, Decision decision, String reason) {
        var entity = repository.findById(approvalId).orElse(null);
        if (entity == null) {
            log.warn("审批请求不存在: {}", approvalId);
            return;
        }
        if (!entity.getUserId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权处理该审批请求");
        }
        if (!"PENDING".equals(entity.getStatus())) {
            log.info("审批已处理，忽略重复决定: id={}, status={}", approvalId, entity.getStatus());
            return;
        }
        entity.setStatus(decision.name());
        entity.setDecisionReason(reason);
        entity.setDecidedAt(Instant.now());
        entity.setDecidedBy(userId);
        repository.save(entity);
        log.info("工具审批已决定: id={}, decision={}", approvalId, decision);
        eventPublisher.publishEvent(
                new ApprovalResolvedEvent(
                        toRequest(entity, parseContext(entity)),
                        new ApprovalResult(decision, reason)));
    }

    /** 按 sessionId 批量归档审批（AG-UI confirm 场景：不知 approvalId，按会话匹配）。 */
    @Transactional
    public void decideByScope(String sessionId, Long userId, Decision decision, String reason) {
        var scopeKey = sessionId == null || sessionId.isBlank() ? NO_SCOPE : sessionId;
        repository.findByScopeKeyAndStatus(scopeKey, "PENDING").stream()
                .map(ToolApprovalEntity::getApprovalId)
                .toList()
                .forEach(id -> decide(id, userId, decision, reason));
    }

    /**
     * 查询审批结果（AI 侧轮询）。
     *
     * <p>超时判定落库为 TIMEOUT，避免多实例下各自计时得到不同结论。
     */
    @Transactional
    public Optional<ApprovalResult> getResult(String approvalId) {
        var entity = repository.findById(approvalId).orElse(null);
        if (entity == null) {
            return Optional.empty();
        }
        if ("PENDING".equals(entity.getStatus())) {
            if (Instant.now().isAfter(entity.getExpiresAt())) {
                entity.setStatus(Decision.TIMEOUT.name());
                entity.setDecidedAt(Instant.now());
                repository.save(entity);
                return Optional.of(ApprovalResult.timeout());
            }
            return Optional.empty();
        }
        var decision = Decision.valueOf(entity.getStatus());
        return Optional.of(new ApprovalResult(decision, entity.getDecisionReason()));
    }

    /** 查询用户待处理的审批列表。 */
    @Transactional(readOnly = true)
    public List<ApprovalRequest> getPending(Long userId) {
        return repository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING").stream()
                .map(entity -> toRequest(entity, parseContext(entity)))
                .toList();
    }

    private ApprovalRequest toRequest(ToolApprovalEntity entity, Map<String, Object> context) {
        return new ApprovalRequest(
                entity.getApprovalId(),
                NO_SCOPE.equals(entity.getScopeKey()) ? null : entity.getScopeKey(),
                entity.getUserId(),
                ApprovalType.valueOf(entity.getApprovalType()),
                entity.getTitle(),
                entity.getDescription(),
                entity.getSubjectType(),
                entity.getSubjectKey(),
                entity.getRiskLevel(),
                entity.getConfidence(),
                GrantScope.valueOf(entity.getGrantScope()),
                context == null ? Map.of() : context,
                entity.getCreatedAt(),
                Duration.between(entity.getCreatedAt(), entity.getExpiresAt()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseContext(ToolApprovalEntity entity) {
        if (entity.getContextJson() == null || entity.getContextJson().isBlank()) {
            return Map.of();
        }
        var parsed = JsonUtils.parseObjectQuietly(entity.getContextJson(), Map.class);
        return parsed == null ? Map.of() : (Map<String, Object>) parsed;
    }

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
            return GrantScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return GrantScope.NONE;
        }
    }
}
