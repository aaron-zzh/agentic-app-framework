package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;

/**
 * 有效工具交集解析器：RESTRICT/INHERIT 三态语义与 {@link BaseToolProfile} 并入。
 *
 * <p>{@code inheritRoleTools=false}（RESTRICT）时 Skill 空需求恒不放行业务工具，只保留基础工具； {@code
 * inheritRoleTools=true}（INHERIT）时跳过 Skill 必需工具限制，放行 Role/Assistant 与 Agent 交集的全部工具。两种模式下 {@link
 * BaseToolProfile} 中的工具只要在 Agent 声明范围内即恒定并入结果， 与 {@code action-governance.md#有效工具交集} 的
 * RESTRICT/INHERIT 契约一致。
 */
class DefaultEffectiveToolResolverTest {

    private final EffectiveToolResolver resolver = new DefaultEffectiveToolResolver();

    @Test
    @DisplayName("Given RESTRICT 且 Skill 未声明工具需求 When resolve Then 不放行业务工具")
    void should_return_empty_when_restrict_and_skill_declares_no_required_tools() {
        var result =
                resolver.resolve(
                        Set.of(), false, Set.of(), List.of(tool("search"), tool("browser")));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Given RESTRICT 且 Skill 未声明工具需求且 Role 有白名单 When resolve Then 仍不放行任何业务工具")
    void should_return_empty_when_restrict_and_role_whitelist_present_but_skill_empty() {
        var result =
                resolver.resolve(
                        Set.of(),
                        false,
                        Set.of("search"),
                        List.of(tool("search"), tool("browser")));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Given RESTRICT 且 Skill 声明工具需求且 Role 未设置白名单 When resolve Then 按 Skill 需求放行")
    void should_return_skill_required_tools_when_restrict_and_role_whitelist_empty() {
        var search = tool("search");
        var browser = tool("browser");

        var result =
                resolver.resolve(
                        Set.of("search", "browser"), false, Set.of(), List.of(search, browser));

        assertThat(result).containsExactlyInAnyOrder(search, browser);
    }

    @Test
    @DisplayName("Given RESTRICT 且 Role 仅允许部分 Skill 需求工具 When resolve Then 按 Role 白名单收窄")
    void should_restrict_to_role_whitelist_when_skill_requires_more_than_role_allows() {
        var search = tool("search");

        var result =
                resolver.resolve(
                        Set.of("search"),
                        false,
                        Set.of("search"),
                        List.of(search, tool("browser")));

        assertThat(result).containsExactlyInAnyOrder(search);
    }

    @Test
    @DisplayName("Given RESTRICT 且 Role 白名单、Skill 需求与 Agent 工具三层部分重叠 When resolve Then 取三层交集")
    void should_intersect_skill_role_and_agent_tools() {
        var search = tool("search");
        var calculator = tool("calculator");

        var result =
                resolver.resolve(
                        Set.of("search", "calculator"),
                        false,
                        Set.of("search", "calculator", "unknown"),
                        List.of(search, tool("browser"), calculator));

        assertThat(result).containsExactlyInAnyOrder(search, calculator);
    }

    @Test
    @DisplayName("Given RESTRICT 且 Role 白名单排除了 Skill 必需工具 When resolve Then 执行前失败而非静默收窄为空")
    void should_throw_when_role_whitelist_excludes_required_skill_tool() {
        assertThatThrownBy(
                        () ->
                                resolver.resolve(
                                        Set.of("search"),
                                        false,
                                        Set.of("browser"),
                                        List.of(tool("search"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("已激活 Skill 的必需工具不在 Role 与 Agent 交集内");
    }

    @Test
    @DisplayName("Given Skill 要求工具且 Agent 未声明工具 When resolve Then 抛出状态异常")
    void should_throw_when_skill_requires_tools_but_agent_tools_empty() {
        assertThatThrownBy(() -> resolver.resolve(Set.of("search"), false, Set.of(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Agent 未声明工具，无法满足已激活 Skill 的必需工具");
    }

    @Test
    @DisplayName("Given INHERIT When resolve Then 跳过 Skill 必需工具限制放行 Role 与 Agent 交集")
    void should_ignore_skill_requirements_when_inherit_role_tools() {
        var search = tool("search");
        var browser = tool("browser");

        var result =
                resolver.resolve(
                        Set.of(), true, Set.of("search", "browser"), List.of(search, browser));

        assertThat(result).containsExactlyInAnyOrder(search, browser);
    }

    @Test
    @DisplayName("Given INHERIT 且 Role 白名单为空 When resolve Then 放行 Agent 全部声明工具")
    void should_return_all_agent_tools_when_inherit_and_role_whitelist_empty() {
        var search = tool("search");
        var browser = tool("browser");

        var result = resolver.resolve(Set.of(), true, Set.of(), List.of(search, browser));

        assertThat(result).containsExactlyInAnyOrder(search, browser);
    }

    @Test
    @DisplayName("Given INHERIT 且 Role 白名单收窄 Agent 工具 When resolve Then 只放行白名单内工具")
    void should_restrict_inherit_to_role_whitelist() {
        var search = tool("search");

        var result =
                resolver.resolve(
                        Set.of(), true, Set.of("search"), List.of(search, tool("browser")));

        assertThat(result).containsExactlyInAnyOrder(search);
    }

    @Test
    @DisplayName("Given RESTRICT 且 Skill 空需求 When Agent 已声明基础工具 Then 恒定放行 BaseToolProfile")
    void should_include_base_tool_profile_when_restrict_and_skill_empty() {
        var baseTool = tool("queryWeather");

        var result =
                resolver.resolve(Set.of(), false, Set.of(), List.of(baseTool, tool("browser")));

        assertThat(result).containsExactlyInAnyOrder(baseTool);
    }

    @Test
    @DisplayName("Given RESTRICT 且 Skill 声明业务工具 When Agent 同时声明基础工具 Then 结果并入基础工具")
    void should_merge_base_tool_profile_with_restricted_skill_tools() {
        var search = tool("search");
        var baseTool = tool("recognizeOcr");

        var result = resolver.resolve(Set.of("search"), false, Set.of(), List.of(search, baseTool));

        assertThat(result).containsExactlyInAnyOrder(search, baseTool);
    }

    @Test
    @DisplayName("Given INHERIT When Agent 声明基础工具但 Role 白名单未包含 Then 基础工具不因白名单外被排除以外的逻辑重复计入")
    void should_not_duplicate_base_tool_already_covered_by_inherit() {
        var baseTool = tool("listBusinessActions");

        var result =
                resolver.resolve(Set.of(), true, Set.of("listBusinessActions"), List.of(baseTool));

        assertThat(result).containsExactly(baseTool);
    }

    @Test
    @DisplayName("Given RESTRICT 且 Assistant Skill 未声明工具需求 When resolveAssistant Then 不放行业务工具")
    void should_return_empty_when_restrict_and_assistant_skill_declares_no_required_tools() {
        var result =
                resolver.resolveAssistant(
                        Set.of(), false, Set.of("search"), List.of(tool("search")));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Given RESTRICT 且 Assistant 白名单覆盖 Skill 需求 When resolveAssistant Then 按需求放行")
    void should_return_required_tools_when_assistant_whitelist_covers_skill_requirement() {
        var search = tool("search");

        var result =
                resolver.resolveAssistant(
                        Set.of("search"),
                        false,
                        Set.of("search"),
                        List.of(search, tool("browser")));

        assertThat(result).containsExactlyInAnyOrder(search);
    }

    @Test
    @DisplayName("Given RESTRICT 且 Assistant 白名单未覆盖 Skill 必需工具 When resolveAssistant Then 执行前失败")
    void should_throw_when_assistant_whitelist_excludes_required_skill_tool() {
        assertThatThrownBy(
                        () ->
                                resolver.resolveAssistant(
                                        Set.of("search"),
                                        false,
                                        Set.of("browser"),
                                        List.of(tool("search"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Assistant Skill 的必需工具不在 Assistant 与 Agent 交集内");
    }

    @Test
    @DisplayName("Given INHERIT When resolveAssistant Then 跳过 Skill 必需工具限制放行 Assistant 与 Agent 交集")
    void should_ignore_skill_requirements_when_resolve_assistant_inherit() {
        var search = tool("search");

        var result =
                resolver.resolveAssistant(
                        Set.of(), true, Set.of("search"), List.of(search, tool("browser")));

        assertThat(result).containsExactlyInAnyOrder(search);
    }

    @Test
    @DisplayName(
            "Given RESTRICT 且 Assistant Skill 空需求 When Agent 已声明基础工具 Then 恒定放行 BaseToolProfile")
    void should_include_base_tool_profile_when_resolve_assistant_restrict_and_skill_empty() {
        var baseTool = tool("list_workflows");

        var result =
                resolver.resolveAssistant(
                        Set.of(), false, Set.of(), List.of(baseTool, tool("browser")));

        assertThat(result).containsExactlyInAnyOrder(baseTool);
    }

    private static ToolRef tool(String name) {
        return new ToolRef("tool." + name, 1L, name);
    }
}
