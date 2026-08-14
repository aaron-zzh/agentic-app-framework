package com.xuejiai.aaf.framework.engine.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 提示词安全编译器单元测试。 */
class PromptTemplateCompilerTest {

    private final PromptTemplateCompiler compiler = new PromptTemplateCompiler();

    @Test
    @DisplayName("Given 合法变量 When 编译模板 Then 仅执行字面量替换")
    void should_compile_literal_value_when_variables_match() {
        var declarations = compiler.serializeDeclarations("你好 ${name}", null, List.of("name"));

        var result =
                compiler.compile(
                        "你好 ${name}", null, declarations, Map.of("name", "$1\\path"), false);

        assertThat(result).isEqualTo("你好 $1\\path");
    }

    @Test
    @DisplayName("Given 缺少声明变量 When 编译模板 Then 拒绝编译")
    void should_reject_compile_when_required_variable_missing() {
        var declarations = compiler.serializeDeclarations("你好 ${name}", null, List.of("name"));

        assertThatThrownBy(
                        () -> compiler.compile("你好 ${name}", null, declarations, Map.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少");
    }

    @Test
    @DisplayName("Given 表达式占位符 When 保存模板 Then 拒绝非变量语法")
    void should_reject_expression_when_placeholder_is_not_variable() {
        assertThatThrownBy(
                        () ->
                                compiler.serializeDeclarations(
                                        "${T(java.lang.Runtime).getRuntime()}", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法模板占位符");
    }

    @Test
    @DisplayName("Given 未闭合占位符 When 保存模板 Then 拒绝畸形模板")
    void should_reject_unclosed_placeholder_when_serializing() {
        assertThatThrownBy(() -> compiler.serializeDeclarations("你好 ${name", null, List.of("name")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未闭合");
    }

    @Test
    @DisplayName("Given 变量值仅含空白 When 编译模板 Then 拒绝空白变量")
    void should_reject_blank_variable_value_when_compiling() {
        var declarations = compiler.serializeDeclarations("你好 ${name}", null, List.of("name"));

        assertThatThrownBy(
                        () ->
                                compiler.compile(
                                        "你好 ${name}",
                                        null,
                                        declarations,
                                        Map.of("name", "   "),
                                        false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空白");
    }
}
