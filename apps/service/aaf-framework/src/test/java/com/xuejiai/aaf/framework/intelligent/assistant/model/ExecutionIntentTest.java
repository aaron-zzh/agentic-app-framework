package com.xuejiai.aaf.framework.intelligent.assistant.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent.ActionAuthorizationPolicy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent.ArtifactPolicy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent.OutputKind;

class ExecutionIntentTest {

    @Test
    void taskFixedCarriesResolvedRouteAndDraftPolicy() {
        var policy =
                ArtifactPolicy.autoSaveDraft(
                        OutputKind.DOCUMENT, "text/markdown", "content.draft.upsert");

        var intent =
                ExecutionIntent.taskFixed(
                        "system.role.content-creator",
                        "voiceover",
                        3,
                        policy,
                        ActionAuthorizationPolicy.requestOnDemand("content.draft.upsert"),
                        12L);

        assertThat(intent.autoSaveDraft()).isTrue();
        assertThat(intent.resolvedRoute().roleKey()).isEqualTo("system.role.content-creator");
        assertThat(intent.resolvedRoute().skillKey()).isEqualTo("voiceover");
        assertThat(intent.workspaceId()).isEqualTo(12L);
    }

    @Test
    void taskCannotUseAutoRoute() {
        assertThatThrownBy(
                        () ->
                                new ExecutionIntent(
                                        ExecutionIntent.InteractionMode.TASK,
                                        ExecutionIntent.RouteConstraint.AUTO,
                                        ExecutionIntent.ClarificationPolicy.FAIL_ON_BLOCKER,
                                        null,
                                        ArtifactPolicy.returnOnly(
                                                OutputKind.DOCUMENT, "text/markdown"),
                                        ActionAuthorizationPolicy.requestOnDemand(),
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TASK");
    }

    @Test
    void autoSaveRequiresSaveToolAndReturnOnlyRejectsOne() {
        assertThatThrownBy(
                        () ->
                                ArtifactPolicy.autoSaveDraft(
                                        OutputKind.DOCUMENT, "text/markdown", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("saveTool");
        assertThatThrownBy(
                        () ->
                                new ArtifactPolicy(
                                        OutputKind.DOCUMENT,
                                        "text/markdown",
                                        ExecutionIntent.PersistenceMode.RETURN_ONLY,
                                        "content.draft.upsert",
                                        ExecutionIntent.PublishPolicy.NEVER_BY_DEFAULT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RETURN_ONLY");
    }
}
