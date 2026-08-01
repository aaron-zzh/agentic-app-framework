package com.xuejiai.aaf.framework.engine.workflow.condition;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.workflow.condition.ConditionExpression.Logic;

/**
 * 条件表达式求值器——支持嵌套条件组的求值和 Flowable UEL 表达式转换。
 *
 * @author AaronZZH
 */
@Component
public class ConditionEvaluator {

    /**
     * 对条件组求值。
     *
     * @param group 条件组
     * @param formData 表单数据
     * @return 求值结果
     */
    public boolean evaluate(ConditionGroup group, Map<String, Object> formData) {
        if (group == null) return true;

        var logic = group.logic() != null ? group.logic() : Logic.AND;
        boolean result = logic == Logic.AND;

        // 求值条件列表
        if (group.conditions() != null) {
            for (var condition : group.conditions()) {
                boolean matched = evaluateExpression(condition, formData);
                result = combine(result, matched, logic);
            }
        }

        // 求值嵌套子组
        if (group.groups() != null) {
            for (var subGroup : group.groups()) {
                boolean matched = evaluate(subGroup, formData);
                result = combine(result, matched, logic);
            }
        }

        return result;
    }

    /**
     * 编译为 Flowable UEL 表达式 + 变量绑定（B17）。
     *
     * <p>原实现把 {@code value} 作为字符串字面量拼进表达式，且转义方式是 SQL 的 {@code '' }（EL 里单引号 应转义为 {@code
     * \'}），反斜杠也未处理——转义实际无效，构造值可闭合字面量并注入后续表达式。
     *
     * <p>现在**任何值都不进入表达式文本**：每个值绑定为一个流程变量（{@code cv0/cv1/...}），表达式里只出现 经白名单校验的字段名、固定运算符和变量名。调用方必须把
     * {@link CompiledCondition#variables()} 写入流程变量， 否则表达式求值会因变量缺失而失败（fail-closed，不会退化成拼串）。
     *
     * <p>{@code IN}/{@code CONTAINS} 仍会生成 {@code .contains(...)} 方法调用——这是这两个运算符的语义所需，
     * 但方法名是代码里的固定字面量，接收者是白名单字段或绑定变量，外部输入无法控制被调用的方法。
     */
    public CompiledCondition compile(ConditionGroup group) {
        var variables = new java.util.LinkedHashMap<String, Object>();
        var expression = buildExpression(group, variables);
        return new CompiledCondition(expression, java.util.Map.copyOf(variables));
    }

    /** 编译结果：可直接交给 Flowable 的表达式，以及必须一同写入流程的变量绑定。 */
    public record CompiledCondition(String expression, Map<String, Object> variables) {}

    private String buildExpression(ConditionGroup group, Map<String, Object> variables) {
        if (group == null) return "true";

        var logic = group.logic() != null ? group.logic() : Logic.AND;
        var connector = logic == Logic.AND ? " && " : " || ";
        var parts = new java.util.ArrayList<String>();

        if (group.conditions() != null) {
            for (var condition : group.conditions()) {
                parts.add(toUel(condition, variables));
            }
        }

        if (group.groups() != null) {
            for (var subGroup : group.groups()) {
                parts.add("(" + unwrap(buildExpression(subGroup, variables)) + ")");
            }
        }

        return parts.isEmpty() ? "true" : "${" + String.join(connector, parts) + "}";
    }

    /** 子组编译结果带 ${}，嵌套拼接时需要去壳。 */
    private String unwrap(String expression) {
        if (expression.startsWith("${") && expression.endsWith("}")) {
            return expression.substring(2, expression.length() - 1);
        }
        return expression;
    }

    private boolean evaluateExpression(ConditionExpression expr, Map<String, Object> formData) {
        var actual = formData.get(expr.field());
        var expected = expr.value();

        return switch (expr.operator()) {
            case EQ -> equals(actual, expected);
            case NEQ -> !equals(actual, expected);
            case GT -> compare(actual, expected) > 0;
            case GTE -> compare(actual, expected) >= 0;
            case LT -> compare(actual, expected) < 0;
            case LTE -> compare(actual, expected) <= 0;
            case IN -> containsIn(actual, expected);
            case CONTAINS -> actual != null && actual.toString().contains(String.valueOf(expected));
        };
    }

    private boolean combine(boolean current, boolean next, Logic logic) {
        return logic == Logic.AND ? current && next : current || next;
    }

    @SuppressWarnings("unchecked")
    private int compare(Object actual, Object expected) {
        if (actual == null || expected == null) return actual == null ? -1 : 1;
        if (actual instanceof Comparable a && expected instanceof Comparable b) {
            return a.compareTo(b);
        }
        return Double.compare(
                Double.parseDouble(actual.toString()), Double.parseDouble(expected.toString()));
    }

    private boolean equals(Object actual, Object expected) {
        if (actual == null && expected == null) return true;
        if (actual == null || expected == null) return false;
        return actual.toString().equals(expected.toString());
    }

    @SuppressWarnings("unchecked")
    private boolean containsIn(Object actual, Object expected) {
        if (actual == null || expected == null) return false;
        if (expected instanceof Collection<?> col) {
            return col.stream().anyMatch(e -> equals(actual, e));
        }
        // 逗号分隔字符串
        return List.of(expected.toString().split(",")).contains(actual.toString());
    }

    /** 字段名白名单：字母/下划线开头，允许点号分隔 */
    private static final java.util.regex.Pattern FIELD =
            java.util.regex.Pattern.compile("^[A-Za-z_][A-Za-z0-9_.]*$");

    /** B17：绑定变量前缀，避免与业务流程变量冲突 */
    private static final String VALUE_VAR_PREFIX = "cv";

    private String toUel(ConditionExpression expr, Map<String, Object> variables) {
        var field = expr.field();
        if (field == null || !FIELD.matcher(field).matches()) {
            throw new IllegalArgumentException("非法条件字段名: " + field);
        }
        // B17：值一律绑定为变量，不进入表达式文本
        var valueVar = VALUE_VAR_PREFIX + variables.size();
        variables.put(valueVar, bindValue(expr));

        return switch (expr.operator()) {
            case EQ -> field + " == " + valueVar;
            case NEQ -> field + " != " + valueVar;
            case GT -> field + " > " + valueVar;
            case GTE -> field + " >= " + valueVar;
            case LT -> field + " < " + valueVar;
            case LTE -> field + " <= " + valueVar;
            case IN -> valueVar + ".contains(" + field + ")";
            case CONTAINS -> field + ".contains(" + valueVar + ")";
        };
    }

    /** IN 的逗号字符串在内存求值里按列表语义处理，绑定时同样转成 List，保持两条链语义一致。 */
    private Object bindValue(ConditionExpression expr) {
        var value = expr.value();
        if (expr.operator() == ConditionExpression.Operator.IN
                && value instanceof String text
                && !(value instanceof Collection<?>)) {
            return List.of(text.split(",")).stream().map(String::trim).toList();
        }
        return value;
    }
}
