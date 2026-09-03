package com.xuejiai.aaf.module.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.assistant.application.DelegatedTaskCoordinator;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition.Lifecycle;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition.RiskPolicy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition.TemplateOwnership;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantVersion;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent.InteractionMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent.PersistenceMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent.RouteConstraint;
import com.xuejiai.aaf.framework.intelligent.assistant.model.MemoryStrategy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.PersonaSnapshot;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ToolPolicy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ToolPolicy.ActionEffect;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ToolPolicy.ToolRule;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.automation.application.DefinitionLifecycleService;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ActionAuthorizationPolicy;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ArtifactPersistence;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ClarificationPolicy;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ExecutionOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.Input;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.KnowledgeMode;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.KnowledgeOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.MemoryMode;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.MemoryOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ModelMode;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ModelSelection;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.OutputOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.RoleSelection;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.SkillSelection;
import com.xuejiai.aaf.module.ai.vision.VisionMediaResolver;

class AssistantExecutionServiceTest {

    @Test
    void mapsCopywritingTaskToServerControlledFixedRouteAndDraftPolicy() {
        var intent =
                AssistantExecutionService.executionIntent(
                        request(
                                AssistantExecutionRequest.InteractionMode.TASK,
                                AssistantExecutionRequest.RouteConstraint.FIXED,
                                ArtifactPersistence.AUTO_SAVE_DRAFT,
                                "voiceover"),
                        definition(),
                        Set.of(),
                        13L);

        assertThat(intent.interactionMode()).isEqualTo(InteractionMode.TASK);
        assertThat(intent.routeConstraint()).isEqualTo(RouteConstraint.FIXED);
        assertThat(intent.resolvedRoute().roleKey()).isEqualTo("system.role.content-creator");
        assertThat(intent.resolvedRoute().skillKey()).isEqualTo("voiceover");
        assertThat(intent.artifactPolicy().canonicalMediaType()).isEqualTo("text/markdown");
        assertThat(intent.artifactPolicy().saveTool()).isEqualTo("content.draft.upsert");
        assertThat(intent.workspaceId()).isEqualTo(13L);
    }

    @Test
    void executeAcceptsAnyPublishedSkillWithoutCapabilityFamilyRestriction() {
        var assistantDefinitions = mock(AssistantDefinitionPort.class);
        var operatorContext = mock(OperatorContext.class);
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(operatorContext.currentOperatorId()).thenReturn(Optional.of(7L));
        when(assistantDefinitions.findDefaultForUser(any(), any()))
                .thenReturn(Optional.of(definition()));
        var service =
                new AssistantExecutionService(
                        mock(DelegatedTaskCoordinator.class),
                        assistantDefinitions,
                        mock(
                                com.xuejiai.aaf.framework.intelligent.assistant.port
                                        .SystemSkillBindingPort.class),
                        mock(DefinitionLifecycleService.class),
                        operatorContext,
                        mock(VisionMediaResolver.class));
        OrgContext.setCurrentOrgId(11L);
        OrgContext.setCurrentWorkspaceId(13L);
        try {
            // 非 copywriting 类目的已发布 Skill 不再被同步拒绝：任务式已通用化
            var stream =
                    service.start(
                            request(
                                    AssistantExecutionRequest.InteractionMode.TASK,
                                    AssistantExecutionRequest.RouteConstraint.FIXED,
                                    ArtifactPersistence.AUTO_SAVE_DRAFT,
                                    "voiceover"),
                            null,
                            "thread-test",
                            "run-test");
            assertThat(stream).isNotNull();
        } finally {
            OrgContext.clear();
        }
    }

    @Test
    void explicitReturnOnlyOverridesDefaultDraftPersistence() {
        var intent =
                AssistantExecutionService.executionIntent(
                        request(
                                AssistantExecutionRequest.InteractionMode.TASK,
                                AssistantExecutionRequest.RouteConstraint.FIXED,
                                ArtifactPersistence.RETURN_ONLY,
                                "redbook"),
                        definition(),
                        Set.of(),
                        null);

        assertThat(intent.artifactPolicy().persistenceMode())
                .isEqualTo(PersistenceMode.RETURN_ONLY);
        assertThat(intent.artifactPolicy().saveTool()).isNull();
    }

    @Test
    void mapsInteractiveRequestToConversationalAuto() {
        var intent =
                AssistantExecutionService.executionIntent(
                        request(
                                AssistantExecutionRequest.InteractionMode.CONVERSATIONAL,
                                AssistantExecutionRequest.RouteConstraint.AUTO,
                                ArtifactPersistence.RETURN_ONLY,
                                null),
                        definition(),
                        Set.of(),
                        8L);

        assertThat(intent.interactionMode()).isEqualTo(InteractionMode.CONVERSATIONAL);
        assertThat(intent.routeConstraint()).isEqualTo(RouteConstraint.AUTO);
        assertThat(intent.resolvedRoute()).isNull();
    }

    @Test
    void rejectsSkillOutsideRequestedRole() {
        assertThatThrownBy(
                        () ->
                                AssistantExecutionService.executionIntent(
                                        request(
                                                AssistantExecutionRequest.InteractionMode.TASK,
                                                AssistantExecutionRequest.RouteConstraint.FIXED,
                                                ArtifactPersistence.AUTO_SAVE_DRAFT,
                                                "not-in-content-role"),
                                        definition(),
                                        Set.of(),
                                        8L))
                .hasMessageContaining("ON_DEMAND");
    }

    private static AssistantDefinition definition() {
        var platformGuide =
                new Role(
                        "system.role.platform-guide",
                        "平台向导",
                        List.of("产品咨询"),
                        List.of("修改用户数据"),
                        List.of(binding("builtin-self-learning", SkillActivationMode.ON_DEMAND)),
                        Set.of("support.handoff"));
        var contentCreator =
                new Role(
                        "system.role.content-creator",
                        "内容创作者",
                        List.of("生成草稿"),
                        List.of("自动发布"),
                        List.of(
                                binding("aigc-copywriting", SkillActivationMode.ON_DEMAND),
                                binding("voiceover", SkillActivationMode.ON_DEMAND),
                                binding("redbook", SkillActivationMode.ON_DEMAND)),
                        Set.of("content.draft.upsert"));
        return new AssistantDefinition(
                new AssistantId("test.assistant.execution"),
                null,
                null,
                TemplateOwnership.USER_OWNED,
                new AssistantVersion(1),
                "7",
                new PersonaSnapshot("persona:test", 1, "测试助理", "测试", "审慎", "简洁", "仅测试", null),
                List.of(platformGuide, contentCreator),
                List.of(),
                Set.of(),
                platformGuide.key(),
                MemoryStrategy.hybridDefault(),
                null,
                new ToolPolicy(
                        Map.of(
                                "support.handoff",
                                new ToolRule(
                                        "support.handoff",
                                        ActionEffect.HUMAN_HANDOFF,
                                        false,
                                        false),
                                "content.draft.upsert",
                                new ToolRule(
                                        "content.draft.upsert",
                                        ActionEffect.REVERSIBLE_WRITE,
                                        true,
                                        true))),
                Set.of(ControlMode.READ_ONLY, ControlMode.COLLABORATIVE, ControlMode.DELEGATED),
                RiskPolicy.CONFIRM_WRITES,
                Lifecycle.PUBLISHED);
    }

    private static SkillBinding binding(String key, SkillActivationMode activationMode) {
        return new SkillBinding(key, activationMode);
    }

    private static AssistantExecutionRequest request(
            AssistantExecutionRequest.InteractionMode interactionMode,
            AssistantExecutionRequest.RouteConstraint routeConstraint,
            ArtifactPersistence persistence,
            String skill) {
        return new AssistantExecutionRequest(
                null,
                new ExecutionOptions(
                        interactionMode,
                        routeConstraint,
                        ClarificationPolicy.FAIL_ON_BLOCKER,
                        ActionAuthorizationPolicy.REQUEST_ON_DEMAND,
                        persistence),
                new Input("测试", Map.of(), List.of()),
                interactionMode == AssistantExecutionRequest.InteractionMode.TASK
                        ? new RoleSelection("system.role.content-creator")
                        : null,
                skill == null ? null : new SkillSelection(skill),
                new KnowledgeOptions(KnowledgeMode.DEFAULT, Set.of(), 5, 0.2),
                new ModelSelection(ModelMode.AUTO, null),
                new MemoryOptions(MemoryMode.DEFAULT),
                new OutputOptions(null, null, null));
    }
}
