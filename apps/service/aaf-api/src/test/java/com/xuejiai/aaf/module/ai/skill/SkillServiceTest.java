package com.xuejiai.aaf.module.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SkillServiceTest {

    @Test
    void normalizesCategoryCodesBeforePolicyValidation() {
        assertThat(SkillService.normalizeCategoryCodes(List.of(" copywriting ")))
                .containsExactly("copywriting");
    }

    @Test
    void rejectsCopywritingSkillWithoutDraftTool() {
        assertThatThrownBy(
                        () ->
                                SkillService.validateCopywritingArtifactPolicy(
                                        Set.of("copywriting"), Set.of("knowledge.search")))
                .hasMessageContaining("content.draft.upsert");
    }

    @Test
    void acceptsCopywritingSkillWithDraftTool() {
        assertThatCode(
                        () ->
                                SkillService.validateCopywritingArtifactPolicy(
                                        Set.of("copywriting"), Set.of("content.draft.upsert")))
                .doesNotThrowAnyException();
    }

    @Test
    void leavesOtherSkillCategoriesUnchanged() {
        assertThatCode(
                        () ->
                                SkillService.validateCopywritingArtifactPolicy(
                                        Set.of("analysis"), Set.of()))
                .doesNotThrowAnyException();
    }
}
