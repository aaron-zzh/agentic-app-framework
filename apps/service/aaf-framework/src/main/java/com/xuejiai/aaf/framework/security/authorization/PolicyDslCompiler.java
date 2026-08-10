package com.xuejiai.aaf.framework.security.authorization;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 将受限 JSON 编译为封闭 AST；不使用 SpEL、反射或任意方法调用。 */
@Component
public final class PolicyDslCompiler {

    private static final JsonMapper SECURITY_DSL_JSON_MAPPER =
            JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    private final PolicyDslLimits limits;

    public PolicyDslCompiler() {
        this(PolicyDslLimits.defaults());
    }

    public PolicyDslCompiler(PolicyDslLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public PolicyExpression compile(String conditionJson, PolicyFactSchema schema) {
        Objects.requireNonNull(schema, "schema");
        if (conditionJson == null || conditionJson.isBlank()) {
            return new PolicyExpression.Constant(true);
        }
        if (conditionJson.length() > limits.maxJsonLength()) {
            throw new PolicyCompilationException("策略 JSON 长度超过限制");
        }
        final JsonNode root;
        try {
            root = SECURITY_DSL_JSON_MAPPER.readTree(conditionJson);
        } catch (Exception ex) {
            throw new PolicyCompilationException("策略不是合法 JSON", ex);
        }
        return compileNode(root, schema, 1, new Counter());
    }

    private PolicyExpression compileNode(
            JsonNode node, PolicyFactSchema schema, int depth, Counter counter) {
        if (depth > limits.maxDepth()) {
            throw new PolicyCompilationException("策略嵌套深度超过限制");
        }
        if (++counter.nodes > limits.maxNodes()) {
            throw new PolicyCompilationException("策略节点数超过限制");
        }
        if (node == null || node.isNull() || !node.isObject()) {
            throw new PolicyCompilationException("策略节点必须是 JSON 对象");
        }
        if (node.isEmpty()) {
            return new PolicyExpression.Constant(true);
        }

        var combinators =
                (node.has("and") ? 1 : 0) + (node.has("or") ? 1 : 0) + (node.has("not") ? 1 : 0);
        if (combinators > 0) {
            return compileCombination(node, schema, depth, counter, combinators);
        }
        return compilePredicate(node, schema);
    }

    private PolicyExpression compileCombination(
            JsonNode node, PolicyFactSchema schema, int depth, Counter counter, int combinators) {
        if (combinators != 1 || node.size() != 1) {
            throw new PolicyCompilationException("组合节点只能声明 and、or、not 之一");
        }
        if (node.has("not")) {
            return new PolicyExpression.Not(
                    compileNode(node.get("not"), schema, depth + 1, counter));
        }
        var key = node.has("and") ? "and" : "or";
        var children = node.get(key);
        if (!children.isArray() || children.isEmpty()) {
            throw new PolicyCompilationException(key + " 必须是非空数组");
        }
        requireArrayLimit(children);
        var expressions = new ArrayList<PolicyExpression>();
        for (var child : children) {
            expressions.add(compileNode(child, schema, depth + 1, counter));
        }
        return "and".equals(key)
                ? new PolicyExpression.AllOf(expressions)
                : new PolicyExpression.AnyOf(expressions);
    }

    private PolicyExpression compilePredicate(JsonNode node, PolicyFactSchema schema) {
        if (!node.hasNonNull("field")
                || !node.get("field").isString()
                || !node.hasNonNull("op")
                || !node.get("op").isString()) {
            throw new PolicyCompilationException("叶子节点必须声明字符串 field 和 op");
        }
        var fact = limitedText(node.get("field"), "field");
        var factType = schema.requireType(fact);
        var operator = parseOperator(limitedText(node.get("op"), "op"));
        if (operator == PolicyExpression.Operator.EXISTS) {
            if (node.size() != 2) {
                throw new PolicyCompilationException("exists 不接受 value 或未知字段");
            }
            return new PolicyExpression.Predicate(fact, factType, operator, null);
        }
        if (node.size() != 3 || !node.has("value")) {
            throw new PolicyCompilationException("叶子节点只能声明 field、op、value");
        }
        var expected = compileExpected(node.get("value"), factType, operator);
        return new PolicyExpression.Predicate(fact, factType, operator, expected);
    }

    private Object compileExpected(
            JsonNode value,
            PolicyFactSchema.ValueType factType,
            PolicyExpression.Operator operator) {
        return switch (operator) {
            case EQ, NE -> literal(value, factType);
            case IN, NOT_IN -> {
                if (factType.array()) {
                    throw new PolicyCompilationException(operator + " 左值必须是标量 fact");
                }
                yield literalArray(value, factType);
            }
            case GT, GTE, LT, LTE -> {
                if (factType != PolicyFactSchema.ValueType.NUMBER) {
                    throw new PolicyCompilationException(operator + " 只支持 NUMBER fact");
                }
                yield literal(value, factType);
            }
            case CONTAINS -> {
                if (!factType.array() && factType != PolicyFactSchema.ValueType.STRING) {
                    throw new PolicyCompilationException("CONTAINS 只支持 STRING 或 ARRAY fact");
                }
                yield literal(value, factType.array() ? factType.elementType() : factType);
            }
            case EXISTS -> throw new PolicyCompilationException("exists 不接受 value");
        };
    }

    private Object literal(JsonNode node, PolicyFactSchema.ValueType type) {
        if (node == null || node.isNull()) {
            throw new PolicyCompilationException("策略字面量不允许 null");
        }
        return switch (type) {
            case STRING -> {
                if (!node.isString()) {
                    throw new PolicyCompilationException("策略字面量必须是 STRING");
                }
                yield limitedText(node, "value");
            }
            case NUMBER -> {
                if (!node.isNumber()) {
                    throw new PolicyCompilationException("策略字面量必须是 NUMBER");
                }
                yield normalize(node.decimalValue());
            }
            case BOOLEAN -> {
                if (!node.isBoolean()) {
                    throw new PolicyCompilationException("策略字面量必须是 BOOLEAN");
                }
                yield node.booleanValue();
            }
            case STRING_ARRAY, NUMBER_ARRAY, BOOLEAN_ARRAY ->
                    literalArray(node, type.elementType());
        };
    }

    private List<Object> literalArray(JsonNode node, PolicyFactSchema.ValueType elementType) {
        if (node == null || !node.isArray()) {
            throw new PolicyCompilationException("策略字面量必须是数组");
        }
        requireArrayLimit(node);
        var values = new ArrayList<>();
        for (var child : node) {
            values.add(literal(child, elementType));
        }
        return List.copyOf(values);
    }

    private void requireArrayLimit(JsonNode node) {
        if (node.size() > limits.maxArrayLength()) {
            throw new PolicyCompilationException("策略数组长度超过限制");
        }
    }

    private String limitedText(JsonNode node, String label) {
        var value = node.asString();
        if (value.length() > limits.maxStringLength()) {
            throw new PolicyCompilationException(label + " 字符串长度超过限制");
        }
        return value;
    }

    private PolicyExpression.Operator parseOperator(String operator) {
        try {
            return PolicyExpression.Operator.valueOf(operator.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new PolicyCompilationException("不支持的策略操作符: " + operator, ex);
        }
    }

    private BigDecimal normalize(BigDecimal number) {
        return number.stripTrailingZeros();
    }

    private static final class Counter {
        private int nodes;
    }
}
