package com.xuejiai.aaf.module.ai.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.assistant.application.DelegatedTaskCoordinator;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent.InteractionMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent.PersistenceMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent.RouteConstraint;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.automation.application.DefinitionLifecycleService;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.DefaultUserAssistantTemplate;
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
import com.xuejiai.aaf.module.ai.skill.SkillService;
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
    void executeValidatesActualAssistantRoleAndCopywritingDirectoryTogether() {
        var assistantDefinitions = mock(AssistantDefinitionPort.class);
        var operatorContext = mock(OperatorContext.class);
        var skillService = mock(SkillService.class);
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
                        mock(VisionMediaResolver.class),
                        skillService);
        OrgContext.setCurrentOrgId(11L);
        OrgContext.setCurrentWorkspaceId(13L);
        try {
            service.start(
                    request(
                            AssistantExecutionRequest.InteractionMode.TASK,
                            AssistantExecutionRequest.RouteConstraint.FIXED,
                            ArtifactPersistence.AUTO_SAVE_DRAFT,
                            "voiceover"),
                    "thread-test",
                    "run-test");
        } finally {
            OrgContext.clear();
        }

        verify(skillService).requireVisiblePublished("voiceover", "copywriting");
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

    private static com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition
            definition() {
        return new DefaultUserAssistantTemplate().templates().getFirst();
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
