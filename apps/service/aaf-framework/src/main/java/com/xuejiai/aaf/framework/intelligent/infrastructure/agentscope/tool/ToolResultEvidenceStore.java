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

/** 在工具返回与 AgentScope 完成事件之间传递脱敏业务证据。 */
public final class ToolResultEvidenceStore {

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
        safe.put("authorizationRequired", authorizationRequired);
        evidence.put(new EvidenceKey(executionId.value(), toolCallId), Map.copyOf(safe));
    }

    public Optional<Map<String, Object>> take(ExecutionId executionId, String toolCallId) {
        return Optional.ofNullable(evidence.remove(new EvidenceKey(executionId.value(), toolCallId)));
    }

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
