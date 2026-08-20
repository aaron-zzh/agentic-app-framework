package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ToolPolicy;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillVersionRef;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class DefaultUserAssistantTemplateTest extends BaseMockitoUnitTest {

    @Mock private SkillCatalogPort skillCatalog;

    private AssistantDefinition template;
    private EffectiveSkillResolver skillResolver;
    private EffectiveToolResolver toolResolver;

    @BeforeEach
    void setUp() {
        template = new DefaultUserAssistantTemplate().templates().getFirst();
        skillResolver = new DefaultEffectiveSkillResolver(skillCatalog);
        toolResolver = new DefaultEffectiveToolResolver();
    }

    @Test
    @DisplayName("Given 系统默认用户助理模板 When 查看当前定义 Then v6 默认平台向导且无旧 Agent 标识")
    void should_define_v6_default_user_assistant_with_default_platform_guide() {
        assertThat(template.assistantId().value())
                .isEqualTo(DefaultUserAssistantTemplate.ASSISTANT_ID);
        assertThat(template.version()).isEqualTo(DefaultUserAssistantTemplate.VERSION);
        assertThat(template.defaultRole().key())
                .isEqualTo(DefaultUserAssistantTemplate.PLATFORM_GUIDE_ROLE_KEY);
        assertThat(template.roles())
                .extracting(Role::key)
                .containsExactlyInAnyOrder(
                        DefaultUserAssistantTemplate.PLATFORM_GUIDE_ROLE_KEY,
                        DefaultUserAssistantTemplate.CONTENT_CREATOR_ROLE_KEY)
                .allSatisfy(key -> assertThat(key).doesNotStartWith("system.agent."));
        assertThat(
                        template.requireRole(DefaultUserAssistantTemplate.CONTENT_CREATOR_ROLE_KEY)
                                .skillKeys())
                .containsExactlyInAnyOrderElementsOf(
                        DefaultUserAssistantTemplate.CONTENT_CREATION_SKILL_KEYS);
        assertThat(template.capabilityManifest().skillKeys())
                .contains(
                        "builtin-user-understanding",
                        "aigc-copywriting",
                        "content-schedule",
                        "content-judge",
                        "content-clarify",
                        "content-architect",
                        "content-build")
                .allSatisfy(key -> assertThat(key).doesNotStartWith("system.agent."));
    }

    @Test
    @DisplayName("Given Role 声明技能范围 When 解析有效技能 Then 仅加载该 Role 内已批准版本")
    void should_resolve_only_approved_skills_within_role_scope() {
        var skillsByCode = skillsByCode();
        when(skillCatalog.findByCode(anyString()))
                .thenAnswer(
                        invocation ->
                                Optional.ofNullable(skillsByCode.get(invocation.getArgument(0))));

        for (var role : template.roles()) {
            var result = skillResolver.resolve(template.candidateSkillKeys(role), role.skillKeys());

            assertThat(result)
                    .extracting(SkillDef::name)
                    .containsExactlyInAnyOrderElementsOf(role.skillKeys());
        }
    }

    @Test
    @DisplayName("Given 默认助理的两个 Role When 解析有效工具 Then 不泄漏另一个 Role 的工具")
    void should_restrict_tools_to_the_effective_role() {
        var roleTools =
                template.roles().stream()
                        .flatMap(role -> role.toolKeys().stream())
                        .distinct()
                        .map(DefaultUserAssistantTemplateTest::tool)
                        .toList();
        var unrestrictedTools = new ArrayList<>(roleTools);
        unrestrictedTools.add(tool("system.unrestricted"));

        for (var role : template.roles()) {
            var result = toolResolver.resolve(Set.of(), role.toolKeys(), unrestrictedTools);

            assertThat(result)
                    .extracting(ToolRef::name)
                    .containsExactlyInAnyOrderElementsOf(role.toolKeys());
        }
    }

    @Test
    @DisplayName("Given 内容创作 Role When 查看工具边界 Then 使用受控草稿 upsert 且不允许发布")
    void should_use_governed_draft_upsert_without_publish_tool() {
        var contentRole =
                template.roles().stream()
                        .filter(
                                role ->
                                        role.key()
                                                .equals(
                                                        DefaultUserAssistantTemplate
                                                                .CONTENT_CREATOR_ROLE_KEY))
                        .findFirst()
                        .orElseThrow();

        assertThat(contentRole.toolKeys())
                .contains("content.draft.upsert")
                .noneMatch(tool -> tool.toLowerCase(java.util.Locale.ROOT).contains("publish"));
        assertThat(template.toolPolicy().rules().get("content.draft.upsert").reversible()).isTrue();
    }

    @Test
    @DisplayName("Given 不存在的默认 Role When 构造定义 Then 拒绝定义")
    void should_reject_missing_default_role() {
        assertThatThrownBy(
                        () ->
                                rebuild(
                                        template.roles(),
                                        "system.role.missing",
                                        template.toolPolicy()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultRoleKey");
    }

    @Test
    @DisplayName("Given ToolPolicy 少于所有 Role 工具并集 When 构造定义 Then 拒绝定义")
    void should_reject_tool_policy_not_equal_to_role_tool_union() {
        var rules = new LinkedHashMap<>(template.toolPolicy().rules());
        rules.remove("support.handoff");

        assertThatThrownBy(
                        () ->
                                rebuild(
                                        template.roles(),
                                        template.defaultRoleKey(),
                                        new ToolPolicy(rules)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("并集");
    }

    private AssistantDefinition rebuild(
            List<Role> roles, String defaultRoleKey, ToolPolicy toolPolicy) {
        return new AssistantDefinition(
                template.assistantId(),
                template.systemKey(),
                template.sourceSystemKey(),
                template.ownership(),
                template.version(),
                template.maintainer(),
                template.actor(),
                roles,
                template.assistantSkillBindings(),
                template.assistantToolKeys(),
                defaultRoleKey,
                template.memoryStrategy(),
                template.modelId(),
                toolPolicy,
                template.supportedControlModes(),
                template.defaultRiskPolicy(),
                template.lifecycle());
    }

    private LinkedHashMap<String, SkillDef> skillsByCode() {
        var result = new LinkedHashMap<String, SkillDef>();
        template.roles().stream()
                .flatMap(role -> role.skillKeys().stream())
                .distinct()
                .forEach(code -> result.put(code, skill(result.size() + 1L, code)));
        return result;
    }

    private static SkillDef skill(long id, String code) {
        return new SkillDef(
                id,
                code,
                code,
                code + "描述",
                new SkillVersionRef(id, id, 1),
                code + "提示词",
                Set.of(),
                Set.of(),
                false);
    }

    private static ToolRef tool(String name) {
        return new ToolRef("tool." + name, 1L, name);
    }
}
