package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.xuejiai.aaf.framework.intelligent.assistant.application.DefaultSkillRouter;
import com.xuejiai.aaf.framework.intelligent.assistant.application.EffectiveSkillResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.application.EffectiveToolResolver;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillRoute;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ToolPolicy;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
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
    @DisplayName("Given 系统默认用户助理模板 When 查看定义和路由 Then 默认平台向导并可切换内容创作者")
    void should_define_one_default_user_assistant_with_two_routable_roles() {
        var templates = new DefaultUserAssistantTemplate().templates();
        var router = new DefaultSkillRouter();

        assertThat(templates).hasSize(1);
        assertThat(template.assistantId().value())
                .isEqualTo(DefaultUserAssistantTemplate.ASSISTANT_ID);
        assertThat(template.defaultRoleKey())
                .isEqualTo(DefaultUserAssistantTemplate.PLATFORM_GUIDE_ROLE_KEY);
        assertThat(template.roles())
                .extracting(Role::key)
                .containsExactlyInAnyOrder(
                        DefaultUserAssistantTemplate.PLATFORM_GUIDE_ROLE_KEY,
                        DefaultUserAssistantTemplate.CONTENT_CREATOR_ROLE_KEY);

        var userId = new UserId("1");
        var defaultRoute = router.route(template, "你好", userId).orElseThrow();
        var contentRoute = router.route(template, "请帮我写一篇文章草稿", userId).orElseThrow();
        assertThat(defaultRoute.skillKey()).isEqualTo("support.read");
        assertThat(defaultRoute.handlingMode()).isEqualTo(SkillRoute.HandlingMode.DIRECT);
        assertThat(template.roleFor(defaultRoute).key())
                .isEqualTo(DefaultUserAssistantTemplate.PLATFORM_GUIDE_ROLE_KEY);
        assertThat(contentRoute.skillKey()).isEqualTo("content.draft");
        assertThat(contentRoute.handlingMode()).isEqualTo(SkillRoute.HandlingMode.DELEGATE);
        assertThat(template.roleFor(contentRoute).key())
                .isEqualTo(DefaultUserAssistantTemplate.CONTENT_CREATOR_ROLE_KEY);
        assertThat(template.capabilityManifest().roleKeys())
                .containsExactlyInAnyOrder(
                        DefaultUserAssistantTemplate.PLATFORM_GUIDE_ROLE_KEY,
                        DefaultUserAssistantTemplate.CONTENT_CREATOR_ROLE_KEY);
    }

    @Test
    @DisplayName("Given 默认助理 Route When 解析有效技能 Then 只加载当前命中的 Skill")
    void should_resolve_only_the_skill_selected_by_route() {
        var skillsByCode = skillsByCode();
        when(skillCatalog.findByCode(anyString()))
                .thenAnswer(
                        invocation ->
                                Optional.ofNullable(skillsByCode.get(invocation.getArgument(0))));

        for (var route : template.skillRoutes()) {
            var result = skillResolver.resolve(template.roleFor(route), route.skillKey());

            assertThat(result).extracting(SkillDef::name).containsExactly(route.skillKey());
        }
    }

    @Test
    @DisplayName("Given 默认助理的两个 Role When 解析有效工具 Then 不泄漏另一个 Role 的工具")
    void should_restrict_tools_to_the_effective_role() {
        var agentTools =
                template.roles().stream()
                        .flatMap(role -> role.toolKeys().stream())
                        .distinct()
                        .map(DefaultUserAssistantTemplateTest::tool)
                        .toList();
        var unrestrictedTools = new ArrayList<>(agentTools);
        unrestrictedTools.add(tool("system.unrestricted"));

        for (var role : template.roles()) {
            var result = toolResolver.resolve(role.toolKeys(), unrestrictedTools);

            assertThat(result)
                    .extracting(ToolRef::name)
                    .containsExactlyInAnyOrderElementsOf(role.toolKeys());
        }
        assertThat(
                        toolResolver.resolve(
                                template.requireRole(
                                                DefaultUserAssistantTemplate
                                                        .PLATFORM_GUIDE_ROLE_KEY)
                                        .toolKeys(),
                                unrestrictedTools))
                .extracting(ToolRef::name)
                .doesNotContain("content.generate", "content.draft.create");
        assertThat(
                        toolResolver.resolve(
                                template.requireRole(
                                                DefaultUserAssistantTemplate
                                                        .CONTENT_CREATOR_ROLE_KEY)
                                        .toolKeys(),
                                unrestrictedTools))
                .extracting(ToolRef::name)
                .doesNotContain("support.diagnostics.read", "support.handoff");
    }

    @Test
    @DisplayName("Given 不存在的默认 Role When 构造定义 Then 拒绝定义")
    void should_reject_missing_default_role() {
        assertThatThrownBy(
                        () ->
                                rebuild(
                                        template.roles(),
                                        "system.role.missing",
                                        template.skillRoutes(),
                                        template.toolPolicy()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultRoleKey");
    }

    @Test
    @DisplayName("Given Route 引用不存在 Role When 构造定义 Then 拒绝定义")
    void should_reject_route_with_missing_role() {
        var routes = new ArrayList<>(template.skillRoutes());
        routes.set(0, routeWithRole(routes.getFirst(), "system.role.missing"));

        assertThatThrownBy(
                        () ->
                                rebuild(
                                        template.roles(),
                                        template.defaultRoleKey(),
                                        routes,
                                        template.toolPolicy()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未配置 Role");
    }

    @Test
    @DisplayName("Given Route 技能不属于所绑 Role When 构造定义 Then 拒绝定义")
    void should_reject_route_with_skill_outside_bound_role() {
        var routes = new ArrayList<>(template.skillRoutes());
        routes.set(
                0,
                routeWithRole(
                        routes.getFirst(), DefaultUserAssistantTemplate.PLATFORM_GUIDE_ROLE_KEY));

        assertThatThrownBy(
                        () ->
                                rebuild(
                                        template.roles(),
                                        template.defaultRoleKey(),
                                        routes,
                                        template.toolPolicy()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role.skillKeys");
    }

    @Test
    @DisplayName("Given ToolPolicy 少于所有 Role 工具并集 When 构造定义 Then 拒绝定义")
    void should_reject_tool_policy_not_equal_to_role_tool_union() {
        var rules = new LinkedHashMap<>(template.toolPolicy().rules());
        rules.remove("content.generate");

        assertThatThrownBy(
                        () ->
                                rebuild(
                                        template.roles(),
                                        template.defaultRoleKey(),
                                        template.skillRoutes(),
                                        new ToolPolicy(rules)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("并集");
    }

    private AssistantDefinition rebuild(
            List<Role> roles,
            String defaultRoleKey,
            List<SkillRoute> routes,
            ToolPolicy toolPolicy) {
        return new AssistantDefinition(
                template.assistantId(),
                template.systemKey(),
                template.sourceSystemKey(),
                template.ownership(),
                template.version(),
                template.maintainer(),
                template.actor(),
                roles,
                defaultRoleKey,
                template.memoryStrategy(),
                routes,
                toolPolicy,
                template.supportedControlModes(),
                template.defaultRiskPolicy(),
                template.lifecycle());
    }

    private SkillRoute routeWithRole(SkillRoute route, String roleKey) {
        return new SkillRoute(
                route.skillKey(),
                roleKey,
                route.intentTerms(),
                route.subagentSpec(),
                route.actionKey(),
                route.actionEffect(),
                route.handlingMode(),
                route.priority(),
                route.defaultRoute());
    }

    private LinkedHashMap<String, SkillDef> skillsByCode() {
        var result = new LinkedHashMap<String, SkillDef>();
        template.roles().stream()
                .flatMap(role -> role.skillKeys().stream())
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
