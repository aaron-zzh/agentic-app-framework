package com.xuejiai.aaf.module.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class SkillStoreImplTest extends BaseMockitoUnitTest {

    @Mock private SkillDefinitionRepository repository;
    @InjectMocks private SkillStoreImpl skillStore;

    @Test
    @DisplayName("Given 启用技能业务码存在 When 读取技能 Then 复用启用状态仓储与记录映射")
    void should_find_and_map_active_skill_by_code() {
        var entity = skill(9L, "content-review");
        when(repository.findByCodeAndStatus("content-review", "active"))
                .thenReturn(Optional.of(entity));

        var result = skillStore.findByCode("content-review");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().skillId()).isEqualTo(9L);
        assertThat(result.orElseThrow().name()).isEqualTo("内容审查");
        assertThat(result.orElseThrow().instructions()).isEqualTo("逐项检查");
        assertThat(result.orElseThrow().builtIn()).isTrue();
        assertThat(result.orElseThrow().global()).isFalse();
        verify(repository).findByCodeAndStatus("content-review", "active");
    }

    @Test
    @DisplayName("Given 业务码没有启用技能 When 读取技能 Then 返回空结果")
    void should_return_empty_when_code_has_no_active_skill() {
        when(repository.findByCodeAndStatus("inactive", "active")).thenReturn(Optional.empty());

        assertThat(skillStore.findByCode("inactive")).isEmpty();
    }

    @Test
    @DisplayName("Given 技能 ID 没有启用技能 When 读取技能 Then 返回空结果")
    void should_return_empty_when_id_has_no_active_skill() {
        when(repository.findByIdAndStatus(9L, "active")).thenReturn(Optional.empty());

        assertThat(skillStore.findBySkillId(9L)).isEmpty();
    }

    private SkillDefinition skill(Long id, String code) {
        var entity = new SkillDefinition();
        entity.setId(id);
        entity.setCode(code);
        entity.setName("内容审查");
        entity.setDescription("检查内容质量");
        entity.setAgentId(3L);
        entity.setTriggerIntent("[\"写作\"]");
        entity.setSystemPrompt("保持审慎");
        entity.setInstructions("逐项检查");
        entity.setPriority(20);
        entity.setBuiltIn(true);
        entity.setIsGlobal(false);
        return entity;
    }
}
