package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;

/**
 * 系统携带的待执行工具调用：轻量态触及授权工具时由网关捕获，随升级写入 Task checkpoint，协调者节点首轮原样重放。
 *
 * <p>只含结构化调用，不含推理过程；参数原文只存 Task 内部 checkpoint，不进入任何公开事件。
 */
public record PendingToolCall(
        String toolCallId,
        String toolName,
        String toolId,
        Map<String, Object> arguments,
        String resource,
        String argumentsDigest,
        ExecutionId originExecutionId,
        Instant capturedAt) {

    public static final String ANNOTATION_KEY = "promotionPendingCall";

    public PendingToolCall {
        toolCallId = requireText(toolCallId, "toolCallId");
        toolName = requireText(toolName, "toolName");
        toolId = requireText(toolId, "toolId");
        // 模型参数可能含 null 值，不能用 Map.copyOf
        arguments =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(Objects.requireNonNull(arguments, "arguments 不能为空")));
        resource = requireText(resource, "resource");
        argumentsDigest = requireText(argumentsDigest, "argumentsDigest");
        Objects.requireNonNull(originExecutionId, "originExecutionId 不能为空");
        Objects.requireNonNull(capturedAt, "capturedAt 不能为空");
    }

    /** 以 {@code sha256(toolId + '|' + canonical(arguments))} 计算参数摘要，与网关 action key 的参数摘要同一算法。 */
    public static String digestOf(String toolId, Map<String, Object> arguments) {
        return sha256(toolId + '|' + canonical(arguments));
    }

    public Map<String, Object> toAnnotation() {
        var values = new LinkedHashMap<String, Object>();
        values.put("toolCallId", toolCallId);
        values.put("toolName", toolName);
        values.put("toolId", toolId);
        values.put("arguments", arguments);
        values.put("resource", resource);
        values.put("argumentsDigest", argumentsDigest);
        values.put("originExecutionId", originExecutionId.value());
        values.put("capturedAt", capturedAt.toString());
        return values;
    }

    /** 读取 checkpoint 注解；结构缺失或类型不符时抛出，不静默忽略。 */
    public static Optional<PendingToolCall> fromAnnotation(Map<String, Object> annotations) {
        Objects.requireNonNull(annotations, "annotations 不能为空");
        var raw = annotations.get(ANNOTATION_KEY);
        if (raw == null) {
            return Optional.empty();
        }
        if (!(raw instanceof Map<?, ?> values)) {
            throw new IllegalStateException(ANNOTATION_KEY + " 必须是对象");
        }
        if (!(values.get("arguments") instanceof Map<?, ?> rawArguments)) {
            throw new IllegalStateException(ANNOTATION_KEY + ".arguments 必须是对象");
        }
        var arguments = new LinkedHashMap<String, Object>();
        rawArguments.forEach((key, value) -> arguments.put(key.toString(), value));
        return Optional.of(
                new PendingToolCall(
                        text(values, "toolCallId"),
                        text(values, "toolName"),
                        text(values, "toolId"),
                        arguments,
                        text(values, "resource"),
                        text(values, "argumentsDigest"),
                        new ExecutionId(text(values, "originExecutionId")),
                        Instant.parse(text(values, "capturedAt"))));
    }

    /** 重放前以同一算法重算摘要，防止 JSONB 往返或人为篡改改变参数。 */
    public void requireDigest(String recomputed) {
        if (!argumentsDigest.equals(recomputed)) {
            throw new IllegalStateException("待执行调用参数摘要不一致: " + toolCallId);
        }
    }

    private static String text(Map<?, ?> values, String key) {
        if (!(values.get(key) instanceof String value) || value.isBlank()) {
            throw new IllegalStateException(ANNOTATION_KEY + "." + key + " 必须是非空字符串");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }

    private static Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((key, item) -> result.put(key.toString(), canonical(item)));
            return result;
        }
        if (value instanceof Iterable<?> values) {
            var result = new ArrayList<>();
            values.forEach(item -> result.add(canonical(item)));
            return result;
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境缺少 SHA-256", failure);
        }
    }
}
