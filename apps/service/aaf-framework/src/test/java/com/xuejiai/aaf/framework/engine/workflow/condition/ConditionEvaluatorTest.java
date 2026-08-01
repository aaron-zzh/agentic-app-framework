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
    void compile_简单EQ_值走变量绑定() {
        var group =
                new ConditionGroup(
                        Logic.AND,
                        List.of(new ConditionExpression("status", Operator.EQ, "approved", null)),
                        null);
        var compiled = evaluator.compile(group);
        assertThat(compiled.expression()).isEqualTo("${status == cv0}");
        assertThat(compiled.variables()).containsEntry("cv0", "approved");
    }

    /** B17：非法字段名（含 SQL/UEL 元字符）必须拒绝，杜绝表达式注入。 */
    @Test
    void compile_非法字段名抛异常() {
        var group =
                new ConditionGroup(
                        Logic.AND,
                        List.of(new ConditionExpression("'; DROP TABLE", Operator.EQ, "x", null)),
                        null);
        assertThatThrownBy(() -> evaluator.compile(group))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法条件字段名");
    }

    /** B17：字段名含方法调用元字符（.getClass(）必须拒绝。 */
    @Test
    void compile_字段名含方法调用抛异常() {
        var group =
                new ConditionGroup(
                        Logic.AND,
                        List.of(new ConditionExpression("x.getClass(", Operator.EQ, "1", null)),
                        null);
        assertThatThrownBy(() -> evaluator.compile(group))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** B17：构造值不再进入表达式文本——引号/反斜杠/表达式片段都只作为变量值存在。 */
    @Test
    void compile_恶意值不进入表达式文本() {
        var payload = "x' || ''.getClass().forName('java.lang.Runtime') || 'y\\";
        var group =
                new ConditionGroup(
                        Logic.AND,
                        List.of(new ConditionExpression("name", Operator.EQ, payload, null)),
                        null);
        var compiled = evaluator.compile(group);

        assertThat(compiled.expression()).isEqualTo("${name == cv0}");
        assertThat(compiled.expression()).doesNotContain("getClass").doesNotContain("'");
        assertThat(compiled.variables()).containsEntry("cv0", payload);
    }

    /** B17：多条件与嵌套子组的变量编号不冲突，且子组不重复包 ${}。 */
    @Test
    void compile_嵌套子组变量编号唯一() {
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

        var compiled = evaluator.compile(outer);

        assertThat(compiled.expression())
                .isEqualTo("${age >= cv0 && (role == cv1 || role == cv2)}");
        assertThat(compiled.variables()).hasSize(3).containsEntry("cv0", 18);
    }

    /** B17：IN 的逗号字符串绑定为列表，与内存求值语义一致。 */
    @Test
    void compile_IN逗号字符串绑定为列表() {
        var group =
                new ConditionGroup(
                        Logic.AND,
                        List.of(
                                new ConditionExpression(
                                        "role", Operator.IN, "admin,manager", null)),
                        null);
        var compiled = evaluator.compile(group);

        assertThat(compiled.expression()).isEqualTo("${cv0.contains(role)}");
        assertThat(compiled.variables()).containsEntry("cv0", List.of("admin", "manager"));
    }
}
