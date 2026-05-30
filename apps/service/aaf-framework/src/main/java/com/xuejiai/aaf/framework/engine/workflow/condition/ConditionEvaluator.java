package com.xuejiai.aaf.framework.engine.workflow.condition;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.workflow.condition.ConditionExpression.Logic;
import com.xuejiai.aaf.framework.engine.workflow.condition.ConditionExpression.Operator;

/**
 * 条件表达式求值器——支持嵌套条件组的求值和 Flowable UEL 表达式转换。
 *
 * @author Kiro
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
     * 转换为 Flowable UEL 表达式。
     *
     * @param group 条件组
     * @return UEL 表达式字符串
     */
    public String toFlowableExpression(ConditionGroup group) {
        if (group == null) return "true";

        var logic = group.logic() != null ? group.logic() : Logic.AND;
        var connector = logic == Logic.AND ? " && " : " || ";
        var parts = new java.util.ArrayList<String>();

        if (group.conditions() != null) {
            for (var condition : group.conditions()) {
                parts.add(toUel(condition));
            }
        }

        if (group.groups() != null) {
            for (var subGroup : group.groups()) {
                parts.add("(" + toFlowableExpression(subGroup) + ")");
            }
        }

        return parts.isEmpty() ? "true" : "${" + String.join(connector, parts) + "}";
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
                Double.parseDouble(actual.toString()),
                Double.parseDouble(expected.toString()));
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

    private String toUel(ConditionExpression expr) {
        var field = expr.field();
        if (field == null || !FIELD.matcher(field).matches()) {
            throw new IllegalArgumentException("非法条件字段名: " + field);
        }
        var value = expr.value();
        String quotedValue;
        if (value instanceof String sv) {
            // 转义单引号防止 UEL 注入
            quotedValue = "'" + sv.replace("'", "''") + "'";
        } else {
            quotedValue = String.valueOf(value);
        }

        return switch (expr.operator()) {
            case EQ -> field + " == " + quotedValue;
            case NEQ -> field + " != " + quotedValue;
            case GT -> field + " > " + quotedValue;
            case GTE -> field + " >= " + quotedValue;
            case LT -> field + " < " + quotedValue;
            case LTE -> field + " <= " + quotedValue;
            case IN -> quotedValue + ".contains(" + field + ")";
            case CONTAINS -> field + ".contains(" + quotedValue + ")";
        };
    }
}
