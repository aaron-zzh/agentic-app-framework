package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;

/**
 * 有效工具交集解析器：Skill 空需求恒为 RESTRICT + 空集合语义。
 *
 * <p>回归背景：{@code resolve()} 此前把 Skill 空需求当作"未限制"（放行 Role/Agent 交集全部工具），而 {@code resolveAssistant()}
 * 把同一空集合当作"全拒绝"（直接返回空列表）—— 同一语义两种解释。已统一为空需求恒拒绝业务工具（BaseToolProfile 落地前等价于"仅基础工具"）， 与 {@code
 * action-governance.md#有效工具交集} 的 RESTRICT + 空集合契约一致。
 */
class DefaultEffectiveToolResolverTest {

    private final EffectiveToolResolver resolver = new DefaultEffectiveToolResolver();

    @Test
    @DisplayName("Given Skill 未声明任何工具需求 When resolve Then 不放行任何业务工具")
    void should_return_empty_when_skill_declares_no_required_tools() {
        var result = resolver.resolve(Set.of(), Set.of(), List.of(tool("search"), tool("browser")));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Given Skill 未声明工具需求且 Role 有白名单 When resolve Then 仍不放行任何业务工具")
    void should_return_empty_when_skill_declares_no_required_tools_even_with_role_whitelist() {
        var result =
                resolver.resolve(
                        Set.of(), Set.of("search"), List.of(tool("search"), tool("browser")));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Given Skill 声明工具需求且 Role 未设置白名单 When resolve Then 按 Skill 需求放行")
    void should_return_skill_required_tools_when_role_whitelist_empty() {
        var search = tool("search");
        var browser = tool("browser");

        var result =
                resolver.resolve(Set.of("search", "browser"), Set.of(), List.of(search, browser));

        assertThat(result).containsExactly(search, browser);
    }

    @Test
    @DisplayName("Given Role 仅允许部分 Skill 需求工具 When resolve Then 按 Role 白名单收窄")
    void should_restrict_to_role_whitelist_when_skill_requires_more_than_role_allows() {
        var search = tool("search");

        var result =
                resolver.resolve(
                        Set.of("search"), Set.of("search"), List.of(search, tool("browser")));

        assertThat(result).containsExactly(search);
    }

    @Test
    @DisplayName("Given Role 白名单、Skill 需求与 Agent 工具三层部分重叠 When resolve Then 取三层交集")
    void should_intersect_skill_role_and_agent_tools() {
        var search = tool("search");
        var calculator = tool("calculator");

        var result =
                resolver.resolve(
                        Set.of("search", "calculator"),
                        Set.of("search", "calculator", "unknown"),
                        List.of(search, tool("browser"), calculator));

        assertThat(result).containsExactly(search, calculator);
    }

    @Test
    @DisplayName("Given Role 白名单排除了 Skill 必需工具 When resolve Then 执行前失败而非静默收窄为空")
    void should_throw_when_role_whitelist_excludes_required_skill_tool() {
        assertThatThrownBy(
                        () ->
                                resolver.resolve(
                                        Set.of("search"),
                                        Set.of("browser"),
                                        List.of(tool("search"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("已激活 Skill 的必需工具不在 Role 与 Agent 交集内");
    }

    @Test
    @DisplayName("Given Skill 要求工具且 Agent 未声明工具 When resolve Then 抛出状态异常")
    void should_throw_when_skill_requires_tools_but_agent_tools_empty() {
        assertThatThrownBy(() -> resolver.resolve(Set.of("search"), Set.of(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Agent 未声明工具，无法满足已激活 Skill 的必需工具");
    }

    @Test
    @DisplayName("Given Assistant Skill 未声明工具需求 When resolveAssistant Then 不放行任何业务工具")
    void should_return_empty_when_assistant_skill_declares_no_required_tools() {
        var result = resolver.resolveAssistant(Set.of(), Set.of("search"), List.of(tool("search")));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Given Assistant 白名单覆盖 Skill 需求 When resolveAssistant Then 按需求放行")
    void should_return_required_tools_when_assistant_whitelist_covers_skill_requirement() {
        var search = tool("search");

        var result =
                resolver.resolveAssistant(
                        Set.of("search"), Set.of("search"), List.of(search, tool("browser")));

        assertThat(result).containsExactly(search);
    }

    @Test
    @DisplayName("Given Assistant 白名单未覆盖 Skill 必需工具 When resolveAssistant Then 执行前失败")
    void should_throw_when_assistant_whitelist_excludes_required_skill_tool() {
        assertThatThrownBy(
                        () ->
                                resolver.resolveAssistant(
                                        Set.of("search"),
                                        Set.of("browser"),
                                        List.of(tool("search"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Assistant Skill 的必需工具不在 Assistant 与 Agent 交集内");
    }

    private static ToolRef tool(String name) {
        return new ToolRef("tool." + name, 1L, name);
    }
}
