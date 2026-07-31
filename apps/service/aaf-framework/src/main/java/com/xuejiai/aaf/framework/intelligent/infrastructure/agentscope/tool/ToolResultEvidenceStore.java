package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;

/**
 * 在工具返回与 AgentScope 完成事件之间传递脱敏业务证据。
 *
 * <p>AgentScope 的 TOOL_RESULT_END 事件不携带 AAF 业务元数据，因此工具执行时先按 (executionId, toolCallId) 暂存，事件映射时 take
 * 取走（一次性消费，不会堆积）。
 */
public final class ToolResultEvidenceStore {

    /** 允许出边界的元数据键白名单，其余一律丢弃。 */
    private static final Set<String> ALLOWED_METADATA_KEYS =
            Set.of(
                    "artifactState",
                    "artifactType",
                    "artifactId",
                    "reversible",
                    "completionEvidence",
                    "approvalId");

    private final ConcurrentMap<EvidenceKey, Map<String, Object>> evidence =
            new ConcurrentHashMap<>();

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
        evidence.put(new EvidenceKey(executionId.value(), toolCallId), Map.copyOf(safe));
    }

    /** 取走并移除证据；同一 toolCallId 只能消费一次。 */
    public Optional<Map<String, Object>> take(ExecutionId executionId, String toolCallId) {
        return Optional.ofNullable(
                evidence.remove(new EvidenceKey(executionId.value(), toolCallId)));
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
}
