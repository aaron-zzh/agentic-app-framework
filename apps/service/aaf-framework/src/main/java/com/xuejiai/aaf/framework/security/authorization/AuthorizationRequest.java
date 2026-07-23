package com.xuejiai.aaf.framework.security.authorization;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** PDP 的统一授权请求，只接受可确定序列化的纯数据事实。 */
public record AuthorizationRequest(
        AuthorizationSubject subject,
        AuthorizationTarget target,
        AuthorizationPlan plan,
        Map<String, Object> facts,
        Duration challengeTtl) {

    private static final int MAX_FACT_DEPTH = 16;
    private static final int MAX_COLLECTION_SIZE = 128;
    private static final int MAX_STRING_LENGTH = 4096;

    public AuthorizationRequest {
        subject = subject == null ? AuthorizationSubject.unresolved() : subject;
        target = target == null ? AuthorizationTarget.none() : target;
        Objects.requireNonNull(plan, "plan");
        facts = facts == null ? Map.of() : immutableData(facts);
        challengeTtl = challengeTtl == null ? Duration.ofMinutes(10) : challengeTtl;
        if (challengeTtl.isZero() || challengeTtl.isNegative()) {
            throw new IllegalArgumentException("challengeTtl 必须大于零");
        }
        canonicalValue(facts, 0);
    }

    public AuthorizationRequest withSubject(AuthorizationSubject resolvedSubject) {
        return new AuthorizationRequest(resolvedSubject, target, plan, facts, challengeTtl);
    }

    /** 深复制并校验纯数据事实，供框架适配层在组装请求前固定可信快照。 */
    public static Map<String, Object> immutableData(Map<?, ?> source) {
        Objects.requireNonNull(source, "source");
        return immutableFacts(source, 0);
    }

    /** 对完整结构化请求生成稳定摘要，调用方不能覆盖。 */
    public String digest() {
        var builder = new StringBuilder();
        append(builder, "operatorId", subject.operatorId());
        append(builder, "subjectId", subject.subjectId());
        append(builder, "tenantId", subject.tenantId());
        append(builder, "workspaceId", subject.workspaceId());
        append(builder, "resource", target.resource());
        append(builder, "action", target.action());
        append(builder, "objectId", target.objectId());
        append(builder, "l1Mode", plan.l1().mode().name());
        append(builder, "permissionCodes", plan.l1().permissionCodes());
        append(builder, "l2", plan.l2());
        append(builder, "l3", plan.l3());
        append(builder, "l4Declared", plan.l4() != null);
        append(builder, "facts", facts);
        return sha256(builder.toString());
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("运行环境缺少 SHA-256", ex);
        }
    }

    private static Map<String, Object> immutableFacts(Map<?, ?> source, int depth) {
        if (depth > MAX_FACT_DEPTH) {
            throw new IllegalArgumentException("授权事实嵌套超过限制");
        }
        if (source.size() > MAX_COLLECTION_SIZE) {
            throw new IllegalArgumentException("授权事实对象字段数超过限制");
        }
        var copy = new LinkedHashMap<String, Object>();
        source.forEach(
                (key, value) ->
                        copy.put(
                                requireStringKey(key), immutableFactValue(value, depth + 1)));
        return Map.copyOf(copy);
    }

    private static Object immutableFactValue(Object value, int depth) {
        if (depth > MAX_FACT_DEPTH) {
            throw new IllegalArgumentException("授权事实嵌套超过限制");
        }
        if (value == null) {
            throw new IllegalArgumentException("授权事实不允许 null，请用缺失字段表达不存在");
        }
        if (value instanceof String text) {
            if (text.length() > MAX_STRING_LENGTH) {
                throw new IllegalArgumentException("授权事实字符串超过限制");
            }
            return text;
        }
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).stripTrailingZeros();
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("授权事实包含非法数字", ex);
            }
        }
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return immutableFacts(map, depth);
        }
        if (value instanceof Iterable<?> iterable) {
            var copy = new ArrayList<>();
            for (var item : iterable) {
                if (copy.size() >= MAX_COLLECTION_SIZE) {
                    throw new IllegalArgumentException("授权事实数组长度超过限制");
                }
                copy.add(immutableFactValue(item, depth + 1));
            }
            return List.copyOf(copy);
        }
        throw new IllegalArgumentException("授权事实包含不支持的类型: " + value.getClass().getName());
    }

    private static void append(StringBuilder builder, String name, Object value) {
        var canonical = canonicalValue(value, 0);
        builder.append(name.length())
                .append(':')
                .append(name)
                .append('=')
                .append(canonical.length())
                .append(':')
                .append(canonical)
                .append(';');
    }

    private static String canonicalValue(Object value, int depth) {
        if (depth > MAX_FACT_DEPTH) {
            throw new IllegalArgumentException("授权事实嵌套超过限制");
        }
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            if (text.length() > MAX_STRING_LENGTH) {
                throw new IllegalArgumentException("授权事实字符串超过限制");
            }
            return "string:" + text;
        }
        if (value instanceof Number number) {
            try {
                return "number:"
                        + new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("授权事实包含非法数字", ex);
            }
        }
        if (value instanceof Boolean flag) {
            return "boolean:" + flag;
        }
        if (value instanceof AuthorizationPlan.RelationPlan relationPlan) {
            return canonicalValue(
                    Map.of(
                            "combination",
                            relationPlan.combination().name(),
                            "requirements",
                            relationPlan.requirements().stream()
                                    .map(
                                            item ->
                                                    Map.of(
                                                            "objectType",
                                                            item.objectType(),
                                                            "objectId",
                                                            item.objectId(),
                                                            "permission",
                                                            item.permission()))
                                    .toList()),
                    depth + 1);
        }
        if (value instanceof AuthorizationPlan.DataPlan dataPlan) {
            return canonicalValue(
                    Map.of(
                            "combination",
                            dataPlan.combination().name(),
                            "requirements",
                            dataPlan.requirements().stream()
                                    .map(
                                            item ->
                                                    Map.of(
                                                            "key",
                                                            item.key(),
                                                            "parameters",
                                                            item.parameters()))
                                    .toList()),
                    depth + 1);
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > MAX_COLLECTION_SIZE) {
                throw new IllegalArgumentException("授权事实对象字段数超过限制");
            }
            var builder = new StringBuilder("map{");
            map.entrySet().stream()
                    .sorted(
                            (left, right) ->
                                    requireStringKey(left.getKey())
                                            .compareTo(requireStringKey(right.getKey())))
                    .forEach(
                            entry -> {
                                var key = requireStringKey(entry.getKey());
                                var item = canonicalValue(entry.getValue(), depth + 1);
                                builder.append(key.length())
                                        .append(':')
                                        .append(key)
                                        .append('=')
                                        .append(item.length())
                                        .append(':')
                                        .append(item)
                                        .append(';');
                            });
            return builder.append('}').toString();
        }
        if (value instanceof Iterable<?> iterable) {
            var builder = new StringBuilder("list[");
            var count = 0;
            for (var item : iterable) {
                if (++count > MAX_COLLECTION_SIZE) {
                    throw new IllegalArgumentException("授权事实数组长度超过限制");
                }
                var canonical = canonicalValue(item, depth + 1);
                builder.append(canonical.length()).append(':').append(canonical).append(';');
            }
            return builder.append(']').toString();
        }
        throw new IllegalArgumentException("授权事实包含不支持的类型: " + value.getClass().getName());
    }

    private static String requireStringKey(Object key) {
        if (!(key instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("授权事实对象键必须是非空字符串");
        }
        return text;
    }
}
