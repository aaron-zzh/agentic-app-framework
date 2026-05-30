package com.xuejiai.aaf.framework.engine.workflow.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.engine.workflow.condition.ConditionExpression.Logic;
import com.xuejiai.aaf.framework.engine.workflow.condition.ConditionExpression.Operator;

/** ConditionEvaluator 单元测试（含 B17 UEL 注入防护）。 */
class ConditionEvaluatorTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    @Test
    void evaluate_null条件组返回true() {
        assertThat(evaluator.evaluate(null, Map.of())).isTrue();
    }

    @Test
    void evaluate_EQ与NEQ() {
        var eq =
                new ConditionGroup(
                        Logic.AND,
                        List.of(new ConditionExpression("status", Operator.EQ, "approved", null)),
                        null);
        assertThat(evaluator.evaluate(eq, Map.of("status", "approved"))).isTrue();
        assertThat(evaluator.evaluate(eq, Map.of("status", "rejected"))).isFalse();
    }

    @Test
    void evaluate_AND逻辑() {
        var group =
                new ConditionGroup(
                        Logic.AND,
                        List.of(
                                new ConditionExpression("age", Operator.GTE, 18, null),
                                new ConditionExpression("status", Operator.EQ, "active", null)),
                        null);
        assertThat(evaluator.evaluate(group, Map.of("age", 20, "status", "active"))).isTrue();
        assertThat(evaluator.evaluate(group, Map.of("age", 20, "status", "inactive"))).isFalse();
    }

    @Test
    void evaluate_嵌套OR子组() {
        var inner =
                new ConditionGroup(
                        Logic.OR,
                        List.of(
                                new ConditionExpression("role", Operator.EQ, "admin", null),
                                new ConditionExpression("role", Operator.EQ, "manager", null)),
                        null);
        var outer =
                new ConditionGroup(
                        Logic.AND,
                        List.of(new ConditionExpression("age", Operator.GTE, 18, null)),
                        List.of(inner));
        assertThat(evaluator.evaluate(outer, Map.of("age", 20, "role", "admin"))).isTrue();
        assertThat(evaluator.evaluate(outer, Map.of("age", 16, "role", "admin"))).isFalse();
    }

    @Test
    void toFlowableExpression_简单EQ() {
        var group =
                new ConditionGroup(
                        Logic.AND,
                        List.of(new ConditionExpression("status", Operator.EQ, "approved", null)),
                        null);
        assertThat(evaluator.toFlowableExpression(group)).isEqualTo("${status == 'approved'}");
    }

    /** B17：非法字段名（含 SQL/UEL 元字符）必须拒绝，杜绝表达式注入。 */
    @Test
    void toFlowableExpression_非法字段名抛异常() {
        var group =
                new ConditionGroup(
                        Logic.AND,
                        List.of(new ConditionExpression("'; DROP TABLE", Operator.EQ, "x", null)),
                        null);
        assertThatThrownBy(() -> evaluator.toFlowableExpression(group))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法条件字段名");
    }

    /** B17：字段名含方法调用元字符（.getClass(）必须拒绝。 */
    @Test
    void toFlowableExpression_字段名含方法调用抛异常() {
        var group =
                new ConditionGroup(
                        Logic.AND,
                        List.of(new ConditionExpression("x.getClass(", Operator.EQ, "1", null)),
                        null);
        assertThatThrownBy(() -> evaluator.toFlowableExpression(group))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** B17：字符串值中的单引号必须转义（' → ''），防止闭合字符串注入表达式。 */
    @Test
    void toFlowableExpression_字符串值单引号被转义() {
        var group =
                new ConditionGroup(
                        Logic.AND,
                        List.of(new ConditionExpression("name", Operator.EQ, "O'Brien", null)),
                        null);
        assertThat(evaluator.toFlowableExpression(group)).isEqualTo("${name == 'O''Brien'}");
    }
}
