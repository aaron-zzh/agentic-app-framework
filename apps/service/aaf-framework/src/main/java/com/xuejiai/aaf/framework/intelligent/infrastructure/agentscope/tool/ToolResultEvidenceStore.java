package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;

import lombok.extern.slf4j.Slf4j;

/**
 * 在工具返回与 AgentScope 完成事件之间传递脱敏业务证据。
 *
 * <p>AgentScope 的 TOOL_RESULT_END 事件不携带 AAF 业务元数据，因此工具执行时先按 (executionId, toolCallId) 暂存，事件映射时 take
 * 取走。
 *
 * <p>正常路径靠 take 消费即可归零，但取消 / 超时 / 映射失败 / 订阅 dispose 会让 TOOL_RESULT_END 永不到达，暂存项因此泄漏（RQ-10）。三道防线：
 *
 * <ul>
 *   <li>执行结束时由调用方无条件 {@link #clear(ExecutionId)} 清空本次执行的全部残留
 *   <li>写入时按 TTL 淘汰过期项，防止 clear 自身被跳过（如进程在 doFinally 前被杀）
 *   <li>容量上限兜底：超限时先按最早写入顺序丢弃，避免无界增长压垮堆内存
 * </ul>
 */
@Slf4j
public final class ToolResultEvidenceStore {

    /** 单项存活上限：超过该时长的证据不可能再有对应 TOOL_RESULT_END 到达。 */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    /** 全局条目上限：正常并发下远达不到，触及即说明存在泄漏路径。 */
    public static final int DEFAULT_MAX_ENTRIES = 10_000;

    /** 允许出边界的元数据键白名单，其余一律丢弃。 */
    private static final Set<String> ALLOWED_METADATA_KEYS =
            Set.of(
                    "artifactState",
                    "artifactType",
                    "artifactId",
                    "reversible",
                    "completionEvidence",
                    "approvalId",
                    "requestId",
                    "clarificationRequired");

    private final ConcurrentMap<EvidenceKey, StoredEvidence> evidence = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;
    private final int maxEntries;

    public ToolResultEvidenceStore() {
        this(Clock.systemUTC(), DEFAULT_TTL, DEFAULT_MAX_ENTRIES);
    }

    public ToolResultEvidenceStore(Clock clock, Duration ttl, int maxEntries) {
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
        this.ttl = Objects.requireNonNull(ttl, "ttl 不能为空");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl 必须大于 0");
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries 必须大于 0");
        }
        this.maxEntries = maxEntries;
    }

    /** 暂存一次工具调用的证据；只保留白名单内的安全标量。 */
    public void record(
            ExecutionId executionId,
            String toolCallId,
            Map<String, Object> metadata,
            boolean reversible,
            boolean authorizationRequired) {
        var safe = new LinkedHashMap<String, Object>();
        if (metadata != null) {
            metadata.forEach(
                    (key, value) -> {
                        if (ALLOWED_METADATA_KEYS.contains(key) && isSafeScalar(value)) {
                            safe.put(key, value);
                        }
                    });
        }
        safe.putIfAbsent("reversible", reversible);
        // 授权标记由目录定义决定，不允许工具自行覆盖
        safe.put("authorizationRequired", authorizationRequired);
        var now = clock.instant();
        evidence.put(
                new EvidenceKey(executionId.value(), toolCallId),
                new StoredEvidence(Map.copyOf(safe), now));
        evictExpired(now);
        evictOverflow();
    }

    /** 查看但不消费证据；仅供事件进入 current/fencing 校验前识别已原子提交的授权挂起。 */
    public Optional<Map<String, Object>> peek(ExecutionId executionId, String toolCallId) {
        return Optional.ofNullable(evidence.get(new EvidenceKey(executionId.value(), toolCallId)))
                .map(StoredEvidence::values);
    }

    /** 取走并移除证据；同一 toolCallId 只能消费一次。 */
    public Optional<Map<String, Object>> take(ExecutionId executionId, String toolCallId) {
        return Optional.ofNullable(
                        evidence.remove(new EvidenceKey(executionId.value(), toolCallId)))
                .map(StoredEvidence::values);
    }

    /** 执行终止时清空该执行的全部残留证据；无论正常完成、失败、取消都必须调用。 */
    public int clear(ExecutionId executionId) {
        Objects.requireNonNull(executionId, "executionId 不能为空");
        var removed = 0;
        var iterator = evidence.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().executionId().equals(executionId.value())) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("[工具证据] 执行结束时清理未消费证据：executionId={}，清理数={}", executionId.value(), removed);
        }
        return removed;
    }

    /** 当前条目数，供测试与监控确认无泄漏。 */
    public int size() {
        return evidence.size();
    }

    private void evictExpired(Instant now) {
        var deadline = now.minus(ttl);
        evidence.entrySet().removeIf(entry -> entry.getValue().recordedAt().isBefore(deadline));
    }

    private void evictOverflow() {
        if (evidence.size() <= maxEntries) {
            return;
        }
        log.warn("[工具证据] 暂存条目超过上限，按写入时间丢弃最旧项：当前={}，上限={}", evidence.size(), maxEntries);
        evidence.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getValue().recordedAt()))
                .limit((long) evidence.size() - maxEntries)
                .map(Map.Entry::getKey)
                .forEach(evidence::remove);
    }

    /** 只放行标量：嵌套结构可能携带敏感明细，NaN/Infinity 无法安全序列化。 */
    private static boolean isSafeScalar(Object value) {
        return value instanceof String
                || value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float number && Float.isFinite(number)
                || value instanceof Double number && Double.isFinite(number)
                || value instanceof BigInteger
                || value instanceof BigDecimal;
    }

    private record EvidenceKey(String executionId, String toolCallId) {}

    private record StoredEvidence(Map<String, Object> values, Instant recordedAt) {}
}
