package com.xuejiai.aaf.module.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;
import com.xuejiai.aaf.framework.engine.skill.SkillModelRequirement;
import com.xuejiai.aaf.framework.engine.skill.SkillToolRequirement;
import com.xuejiai.aaf.framework.engine.skill.SkillVersion;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class SkillStoreImplTest extends BaseMockitoUnitTest {

    @Mock private SkillDefinitionRepository repository;
    @Mock private SkillVersionRepository versionRepository;
    @Mock private SkillToolRequirementRepository toolRequirementRepository;
    @Mock private SkillModelRequirementRepository modelRequirementRepository;
    @InjectMocks private SkillStoreImpl skillStore;

    @Test
    @DisplayName("Given Skill current 指向 APPROVED 版本 When 按 code 读取 Then 返回不可变执行投影")
    void should_return_approved_current_version_when_find_by_code() {
        var definition = definition(9L, 21L, "content-review");
        var version = version(21L, 9L, "APPROVED");
        var tool = toolRequirement(21L, "contentReview");
        var model = modelRequirement(21L, "TEXT");
        when(repository.findByCode("content-review")).thenReturn(Optional.of(definition));
        when(versionRepository.findByIdAndStatus(21L, "APPROVED")).thenReturn(Optional.of(version));
        when(toolRequirementRepository.findBySkillVersionId(21L)).thenReturn(List.of(tool));
        when(modelRequirementRepository.findBySkillVersionId(21L)).thenReturn(List.of(model));

        var result = skillStore.findByCode("content-review").orElseThrow();

        assertThat(result.skillId()).isEqualTo(9L);
        assertThat(result.versionId()).isEqualTo(21L);
        assertThat(result.content()).isEqualTo("逐项检查");
        assertThat(result.requiredToolNames()).containsExactly("contentReview");
        assertThat(result.requiredModelCapabilities()).containsExactly("TEXT");
        assertThat(result.builtIn()).isTrue();
    }

    @Test
    @DisplayName("Given Skill 没有 current 版本 When 读取 Then 返回空结果")
    void should_return_empty_when_skill_has_no_current_version() {
        when(repository.findByCode("draft-only"))
                .thenReturn(Optional.of(definition(9L, null, "draft-only")));

        assertThat(skillStore.findByCode("draft-only")).isEmpty();
    }

    @Test
    @DisplayName("Given current 版本未 APPROVED When 读取 Then 返回空结果")
    void should_return_empty_when_current_version_is_not_approved() {
        when(repository.findById(9L))
                .thenReturn(Optional.of(definition(9L, 21L, "content-review")));
        when(versionRepository.findByIdAndStatus(21L, "APPROVED")).thenReturn(Optional.empty());

        assertThat(skillStore.findBySkillId(9L)).isEmpty();
    }

    private SkillDefinition definition(Long id, Long currentVersionId, String code) {
        var entity = new SkillDefinition();
        entity.setId(id);
        entity.setCode(code);
        entity.setName("内容审查");
        entity.setSummary("检查内容质量");
        entity.setLocale("zh-CN");
        entity.setVisibility("PUBLIC");
        entity.setBuiltIn(true);
        entity.setCurrentVersionId(currentVersionId);
        return entity;
    }

    private SkillVersion version(Long id, Long skillId, String status) {
        var entity = new SkillVersion();
        entity.setId(id);
        entity.setSkillId(skillId);
        entity.setVersion(2);
        entity.setStatus(status);
        entity.setContent("逐项检查");
        entity.setToolAccessMode("RESTRICT");
        entity.setContentHash("hash");
        entity.setCreateTime(LocalDateTime.now());
        return entity;
    }

    private SkillToolRequirement toolRequirement(Long versionId, String toolName) {
        var entity = new SkillToolRequirement();
        entity.setSkillVersionId(versionId);
        entity.setToolId(toolName);
        entity.setToolName(toolName);
        entity.setRequired(true);
        entity.setUsagePurpose("内容审查");
        return entity;
    }

    private SkillModelRequirement modelRequirement(Long versionId, String capability) {
        var entity = new SkillModelRequirement();
        entity.setSkillVersionId(versionId);
        entity.setCapability(capability);
        entity.setRequired(true);
        entity.setRationale("需要文本能力");
        return entity;
    }
}
