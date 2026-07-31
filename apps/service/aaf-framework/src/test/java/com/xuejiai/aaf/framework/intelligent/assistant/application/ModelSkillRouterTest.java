package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillRoute;
import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.DefaultUserAssistantTemplate;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class ModelSkillRouterTest extends BaseMockitoUnitTest {

    @Mock private LlmClient llmClient;
    @Mock private SkillCatalogPort skillCatalog;

    private AssistantDefinition definition;
    private ModelSkillRouter router;

    @BeforeEach
    void setUp() {
        definition = new DefaultUserAssistantTemplate().templates().getFirst();
        router = new ModelSkillRouter(llmClient, skillCatalog);
        when(skillCatalog.findByCode(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Given 语义需要内容创作 Role When 默认模型前注意 Then 委托内容草稿 Skill")
    void should_delegate_to_specialized_role_selected_by_default_model() {
        when(llmClient.call(anyList(), eq("CHAT"), eq(42L)))
                .thenReturn(
                        "{\"roleKey\":\"system.role.content-creator\","
                                + "\"skillKey\":\"content.draft\",\"confidence\":0.96}");

        var route = router.route(definition, "把这些产品更新整理成公众号文章", new UserId("42"));

        assertThat(route).isPresent();
        assertThat(route.orElseThrow().handlingMode())
                .isEqualTo(SkillRoute.HandlingMode.DELEGATE);
        assertThat(route.orElseThrow().skillKey()).isEqualTo("content.draft");
    }

    @Test
    @DisplayName("Given 前注意置信度不足 When 选择 Route Then 默认 Role 直接处理")
    void should_use_default_direct_route_when_confidence_is_low() {
        when(llmClient.call(anyList(), eq("CHAT"), eq(42L)))
                .thenReturn(
                        "{\"roleKey\":\"system.role.content-creator\","
                                + "\"skillKey\":\"content.plan\",\"confidence\":0.40}");

        var route = router.route(definition, "我有一个不太确定的想法", new UserId("42"));

        assertThat(route).isPresent();
        assertThat(route.orElseThrow().roleKey())
                .isEqualTo(DefaultUserAssistantTemplate.PLATFORM_GUIDE_ROLE_KEY);
        assertThat(route.orElseThrow().handlingMode())
                .isEqualTo(SkillRoute.HandlingMode.DIRECT);
    }

    @Test
    @DisplayName("Given 模型返回未授权组合 When 选择 Route Then 使用确定性规则兜底")
    void should_fallback_when_model_returns_unauthorized_route() {
        when(llmClient.call(anyList(), eq("CHAT"), eq(42L)))
                .thenReturn(
                        "{\"roleKey\":\"system.role.missing\","
                                + "\"skillKey\":\"missing\",\"confidence\":0.99}");

        var route = router.route(definition, "请帮我写一篇文章", new UserId("42"));

        assertThat(route).isPresent();
        assertThat(route.orElseThrow().skillKey()).isEqualTo("content.draft");
    }
}
