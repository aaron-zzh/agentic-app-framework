package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;

class DefaultEffectiveToolResolverTest {

    private final EffectiveToolResolver resolver = new DefaultEffectiveToolResolver();

    @Test
    @DisplayName("Given Skill 与 Role 白名单都为空 When resolve Then 返回空工具列表")
    void should_return_empty_when_both_layers_are_unrestricted() {
        var result = resolver.resolve(Set.of(), Set.of(), List.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Given Skill 与 Role 都未限定且 Agent 限定工具 When resolve Then 返回 Agent 全部工具")
    void should_return_agent_tools_when_role_whitelist_empty() {
        var search = tool("search");
        var browser = tool("browser");

        var result = resolver.resolve(Set.of(), Set.of(), List.of(search, browser));

        assertThat(result).containsExactly(search, browser);
    }

    @Test
    @DisplayName("Given Role 仅允许部分 Agent 工具 When resolve Then 按 Role 白名单收窄")
    void should_restrict_agent_tools_when_role_whitelist_present() {
        var search = tool("search");
        var browser = tool("browser");

        var result = resolver.resolve(Set.of(), Set.of("search"), List.of(search, browser));

        assertThat(result).containsExactly(search);
    }

    @Test
    @DisplayName("Given Role 白名单与 Agent 工具部分重叠 When resolve Then 取交集")
    void should_intersect_agent_tools_with_merged_role_whitelist() {
        var search = tool("search");
        var browser = tool("browser");
        var calculator = tool("calculator");

        var result =
                resolver.resolve(
                        Set.of(),
                        Set.of("search", "calculator", "unknown"),
                        List.of(search, browser, calculator));

        assertThat(result).containsExactly(search, calculator);
    }

    @Test
    @DisplayName("Given Role 与 Agent 工具没有交集 When resolve Then 返回空工具列表")
    void should_return_empty_when_whitelists_do_not_overlap() {
        var result = resolver.resolve(Set.of(), Set.of("search"), List.of(tool("browser")));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Given Skill 要求工具且 Agent 未声明工具 When resolve Then 抛出状态异常")
    void should_throw_when_skill_requires_tools_but_agent_tools_empty() {
        assertThatThrownBy(() -> resolver.resolve(Set.of("search"), Set.of(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Agent 未声明工具，无法满足已激活 Skill 的必需工具");
    }

    private static ToolRef tool(String name) {
        return new ToolRef("tool." + name, 1L, name);
    }
}
