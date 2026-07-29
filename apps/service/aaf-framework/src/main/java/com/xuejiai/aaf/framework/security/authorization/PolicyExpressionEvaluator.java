package com.xuejiai.aaf.framework.security.authorization;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** 对已编译 AST 求值，只读取请求模型和 Map 事实。 */
@Component
public final class PolicyExpressionEvaluator {

    public boolean evaluate(PolicyExpression expression, AuthorizationRequest request) {
        return switch (expression) {
            case PolicyExpression.Constant constant -> constant.value();
            case PolicyExpression.AllOf all ->
                    all.expressions().stream().allMatch(child -> evaluate(child, request));
            case PolicyExpression.AnyOf any ->
                    any.expressions().stream().anyMatch(child -> evaluate(child, request));
            case PolicyExpression.Not not -> !evaluate(not.expression(), request);
            case PolicyExpression.Predicate predicate -> evaluatePredicate(predicate, request);
        };
    }

    private boolean evaluatePredicate(
            PolicyExpression.Predicate predicate, AuthorizationRequest request) {
        var raw = resolve(predicate.fact(), request);
        if (predicate.operator() == PolicyExpression.Operator.EXISTS) {
            return raw != Missing.INSTANCE && raw != null;
        }
        if (raw == Missing.INSTANCE || raw == null) {
            return false;
        }
        var actual = normalize(raw, predicate.factType(), predicate.fact());
        var expected = predicate.expected();
        return switch (predicate.operator()) {
            case EQ -> equalValue(actual, expected);
            case NE -> !equalValue(actual, expected);
            case IN -> contains(requireList(expected), actual);
            case NOT_IN -> !contains(requireList(expected), actual);
            case GT -> compare(actual, expected) > 0;
            case GTE -> compare(actual, expected) >= 0;
            case LT -> compare(actual, expected) < 0;
            case LTE -> compare(actual, expected) <= 0;
            case CONTAINS -> containsValue(actual, expected);
            case EXISTS -> throw new IllegalStateException("exists 已在前置分支处理");
        };
    }

    private Object resolve(String fact, AuthorizationRequest request) {
        return switch (fact) {
            case "operatorId" -> request.subject().operatorId();
            case "subjectId" -> request.subject().subjectId();
            case "tenantId" -> request.subject().tenantId();
            case "workspaceId" -> request.subject().workspaceId();
            case "resource" -> request.target().resource();
            case "action" -> request.target().action();
            case "objectId" -> request.target().objectId();
            default -> resolveAttribute(fact.substring("attributes.".length()), request.facts());
        };
    }

    private Object resolveAttribute(String path, Map<String, Object> facts) {
        Object current = facts;
        for (var segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> values) || !values.containsKey(segment)) {
                return Missing.INSTANCE;
            }
            current = values.get(segment);
        }
        return current;
    }

    private Object normalize(Object value, PolicyFactSchema.ValueType type, String factName) {
        return switch (type) {
            case STRING -> {
                if (!(value instanceof String)) {
                    throw typeMismatch(factName, type, value);
                }
                yield value;
            }
            case NUMBER -> normalizeNumber(value, factName);
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) {
                    throw typeMismatch(factName, type, value);
                }
                yield value;
            }
            case STRING_ARRAY, NUMBER_ARRAY, BOOLEAN_ARRAY ->
                    normalizeArray(value, type.elementType(), factName);
        };
    }

    private BigDecimal normalizeNumber(Object value, String factName) {
        if (!(value instanceof Number number)) {
            throw typeMismatch(factName, PolicyFactSchema.ValueType.NUMBER, value);
        }
        try {
            return new BigDecimal(number.toString()).stripTrailingZeros();
        } catch (NumberFormatException ex) {
            throw new PolicyEvaluationException("策略事实 " + factName + " 包含非法数字");
        }
    }

    private List<Object> normalizeArray(
            Object value, PolicyFactSchema.ValueType elementType, String factName) {
        if (!(value instanceof Collection<?> collection)) {
            throw typeMismatch(factName, elementType, value);
        }
        var normalized = new ArrayList<>();
        for (var item : collection) {
            normalized.add(normalize(item, elementType, factName));
        }
        return List.copyOf(normalized);
    }

    private PolicyEvaluationException typeMismatch(
            String factName, PolicyFactSchema.ValueType expected, Object actual) {
        return new PolicyEvaluationException(
                "策略事实 %s 类型不匹配，期望 %s，实际 %s"
                        .formatted(
                                factName,
                                expected,
                                actual == null ? "null" : actual.getClass().getSimpleName()));
    }

    private List<?> requireList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new PolicyEvaluationException("策略 AST 数组字面量损坏");
    }

    private boolean equalValue(Object actual, Object expected) {
        if (actual instanceof BigDecimal left && expected instanceof BigDecimal right) {
            return left.compareTo(right) == 0;
        }
        return actual.equals(expected);
    }

    private boolean contains(List<?> values, Object actual) {
        return values.stream().anyMatch(value -> equalValue(value, actual));
    }

    private boolean containsValue(Object actual, Object expected) {
        if (actual instanceof String text && expected instanceof String part) {
            return text.contains(part);
        }
        if (actual instanceof List<?> values) {
            return contains(values, expected);
        }
        throw new PolicyEvaluationException("contains 左值必须是 STRING 或 ARRAY");
    }

    private int compare(Object actual, Object expected) {
        if (actual instanceof BigDecimal left && expected instanceof BigDecimal right) {
            return left.compareTo(right);
        }
        throw new PolicyEvaluationException("大小比较只支持 NUMBER");
    }

    private enum Missing {
        INSTANCE
    }
}
