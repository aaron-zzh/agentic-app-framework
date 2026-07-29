package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultEffectiveSkillResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultEffectiveToolResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.application.EffectiveSkillResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.application.EffectiveToolResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class BuiltinSystemAssistantTemplatesTest extends BaseMockitoUnitTest {

    @Mock private SkillCatalogPort skillCatalog;

    private List<AssistantDefinition> templates;
    private EffectiveSkillResolver skillResolver;
    private EffectiveToolResolver toolResolver;

    @BeforeEach
    void setUp() {
        templates = new BuiltinSystemAssistantTemplates().templates();
        skillResolver = new DefaultEffectiveSkillResolver(skillCatalog);
        toolResolver = new DefaultEffectiveToolResolver();
    }

    @Test
    @DisplayName("Given 两个内置系统 Role When 解析有效技能 Then 全部 skillKeys 均从技能目录解析")
    void should_resolve_skill_keys_from_both_builtin_roles() {
        // 准备参数
        assertThat(templates).hasSize(2);
        var skillsByCode = skillsByCode();
        when(skillCatalog.findBuiltIn()).thenReturn(List.of());
        when(skillCatalog.findByCode(anyString()))
                .thenAnswer(
                        invocation ->
                                Optional.ofNullable(skillsByCode.get(invocation.getArgument(0))));

        for (var template : templates) {
            // 调用
            var result = skillResolver.resolve(List.of(template.role()));

            // 断言
            assertThat(result)
                    .extracting(SkillDef::name)
                    .containsExactlyInAnyOrderElementsOf(template.role().skillKeys());
        }
    }

    @Test
    @DisplayName("Given 两个内置系统 Role When 解析有效工具 Then toolKeys 均收窄 Agent 工具集")
    void should_restrict_agent_tools_with_tool_keys_from_both_builtin_roles() {
        // 准备参数
        assertThat(templates).hasSize(2);
        var agentTools =
                templates.stream()
                        .flatMap(template -> template.role().toolKeys().stream())
                        .distinct()
                        .map(BuiltinSystemAssistantTemplatesTest::tool)
                        .toList();
        var unrestrictedTools = new ArrayList<>(agentTools);
        unrestrictedTools.add(tool("system.unrestricted"));

        for (var template : templates) {
            // 调用
            var result = toolResolver.resolve(template.role().toolKeys(), unrestrictedTools);

            // 断言
            assertThat(result)
                    .extracting(ToolRef::name)
                    .containsExactlyInAnyOrderElementsOf(template.role().toolKeys());
        }
    }

    private LinkedHashMap<String, SkillDef> skillsByCode() {
        var result = new LinkedHashMap<String, SkillDef>();
        templates.stream()
                .flatMap(template -> template.role().skillKeys().stream())
                .distinct()
                .forEach(code -> result.put(code, skill(result.size() + 1L, code)));
        return result;
    }

    private SkillDef skill(long id, String code) {
        return new SkillDef(id, code, code + "描述", null, List.of(), code + "提示词", 10, false);
    }

    private static ToolRef tool(String name) {
        return new ToolRef("tool." + name, 1L, name);
    }
}
