package com.xuejiai.aaf.framework.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PolicyDslCompilerTest {

    private final PolicyFactSchema schema =
            new PolicyFactSchema(
                    Map.of(
                            "attributes.risk", PolicyFactSchema.ValueType.STRING,
                            "attributes.score", PolicyFactSchema.ValueType.NUMBER,
                            "attributes.tags", PolicyFactSchema.ValueType.STRING_ARRAY));
    private final PolicyDslCompiler compiler = new PolicyDslCompiler();
    private final PolicyExpressionEvaluator evaluator = new PolicyExpressionEvaluator();

    @Test
    @DisplayName("Given 合法严格 DSL When 编译求值 Then 按类型化事实返回 true")
    void should_compile_and_evaluate_typed_expression() {
        var json =
                """
                {
                  "and": [
                    {"field":"attributes.risk","op":"eq","value":"high"},
                    {"field":"attributes.score","op":"gte","value":0.9},
                    {"field":"attributes.tags","op":"contains","value":"write"}
                  ]
                }
                """;

        var expression = compiler.compile(json, schema);
        var matched =
                evaluator.evaluate(
                        expression,
                        request(Map.of("risk", "high", "score", 0.95, "tags", java.util.List.of("write"))));

        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("Given 未知 fact 或 SpEL 字段 When 编译 Then 确定拒绝")
    void should_reject_unknown_fact_and_spel_path() {
        var unknown = "{\"field\":\"attributes.unknown\",\"op\":\"eq\",\"value\":\"x\"}";
        var spel =
                "{\"field\":\"T(java.lang.Runtime).getRuntime()\",\"op\":\"eq\",\"value\":\"x\"}";

        assertThatThrownBy(() -> compiler.compile(unknown, schema))
                .isInstanceOf(PolicyCompilationException.class)
                .hasMessageContaining("未知策略事实");
        assertThatThrownBy(() -> compiler.compile(spel, schema))
                .isInstanceOf(PolicyCompilationException.class);
    }

    @Test
    @DisplayName("Given 额外 JSON 字段或 SpEL 操作符 When 编译 Then 确定拒绝")
    void should_reject_unknown_json_member_and_operator() {
        var extra =
                "{\"field\":\"attributes.risk\",\"op\":\"eq\",\"value\":\"high\",\"spel\":\"#root\"}";
        var operator =
                "{\"field\":\"attributes.risk\",\"op\":\"spel\",\"value\":\"#root\"}";

        assertThatThrownBy(() -> compiler.compile(extra, schema))
                .isInstanceOf(PolicyCompilationException.class);
        assertThatThrownBy(() -> compiler.compile(operator, schema))
                .isInstanceOf(PolicyCompilationException.class);
    }

    @Test
    @DisplayName("Given 重复 field、op 或 value 键 When 编译 Then 作为非法 JSON 拒绝")
    void should_reject_duplicate_predicate_keys() {
        var duplicateField =
                "{\"field\":\"attributes.risk\",\"field\":\"attributes.score\",\"op\":\"eq\",\"value\":\"high\"}";
        var duplicateOperator =
                "{\"field\":\"attributes.risk\",\"op\":\"eq\",\"op\":\"ne\",\"value\":\"high\"}";
        var duplicateValue =
                "{\"field\":\"attributes.risk\",\"op\":\"eq\",\"value\":\"high\",\"value\":\"low\"}";

        assertThatThrownBy(() -> compiler.compile(duplicateField, schema))
                .isInstanceOf(PolicyCompilationException.class)
                .hasMessageContaining("策略不是合法 JSON");
        assertThatThrownBy(() -> compiler.compile(duplicateOperator, schema))
                .isInstanceOf(PolicyCompilationException.class)
                .hasMessageContaining("策略不是合法 JSON");
        assertThatThrownBy(() -> compiler.compile(duplicateValue, schema))
                .isInstanceOf(PolicyCompilationException.class)
                .hasMessageContaining("策略不是合法 JSON");
    }

    @Test
    @DisplayName("Given NUMBER fact 使用字符串字面量 When 编译 Then 禁止隐式转换")
    void should_reject_implicit_literal_type_conversion() {
        var json =
                "{\"field\":\"attributes.score\",\"op\":\"eq\",\"value\":\"0.9\"}";

        assertThatThrownBy(() -> compiler.compile(json, schema))
                .isInstanceOf(PolicyCompilationException.class)
                .hasMessageContaining("NUMBER");
    }

    @Test
    @DisplayName("Given NUMBER fact 运行时为字符串 When 求值 Then 禁止隐式转换")
    void should_reject_implicit_runtime_type_conversion() {
        var expression =
                compiler.compile(
                        "{\"field\":\"attributes.score\",\"op\":\"gte\",\"value\":0.9}",
                        schema);

        assertThatThrownBy(
                        () -> evaluator.evaluate(expression, request(Map.of("score", "0.95"))))
                .isInstanceOf(PolicyEvaluationException.class)
                .hasMessageContaining("类型不匹配");
    }

    @Test
    @DisplayName("Given DSL 超过深度节点数组字符串限制 When 编译 Then 分别拒绝")
    void should_enforce_all_resource_limits() {
        var strict = new PolicyDslCompiler(new PolicyDslLimits(500, 2, 3, 1, 8));
        var deep = "{\"not\":{\"not\":{\"field\":\"action\",\"op\":\"eq\",\"value\":\"read\"}}}";
        var nodes =
                "{\"and\":[{\"field\":\"action\",\"op\":\"eq\",\"value\":\"read\"},{\"field\":\"action\",\"op\":\"eq\",\"value\":\"read\"},{\"field\":\"action\",\"op\":\"eq\",\"value\":\"read\"}]}";
        var array = "{\"field\":\"attributes.risk\",\"op\":\"in\",\"value\":[\"low\",\"high\"]}";
        var string = "{\"field\":\"attributes.risk\",\"op\":\"eq\",\"value\":\"very-high\"}";

        assertThatThrownBy(() -> strict.compile(deep, schema))
                .isInstanceOf(PolicyCompilationException.class);
        assertThatThrownBy(() -> strict.compile(nodes, schema))
                .isInstanceOf(PolicyCompilationException.class);
        assertThatThrownBy(() -> strict.compile(array, schema))
                .isInstanceOf(PolicyCompilationException.class);
        assertThatThrownBy(() -> strict.compile(string, schema))
                .isInstanceOf(PolicyCompilationException.class);
    }

    private AuthorizationRequest request(Map<String, Object> facts) {
        return new AuthorizationRequest(new AuthorizationSubject(7L, 7L, 31L, 41L), new AuthorizationTarget("todo", "read", "9"), AuthorizationPlan.authenticated(), facts, Duration.ofMinutes(10));
    }
}
