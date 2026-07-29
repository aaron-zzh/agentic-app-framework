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
    @DisplayName("Given 技能业务码存在 When 读取技能 Then 复用现有仓储与记录映射")
    void should_find_and_map_skill_by_code() {
        // 准备参数
        var entity = new SkillDefinition();
        entity.setId(9L);
        entity.setCode("content-review");
        entity.setName("内容审查");
        entity.setDescription("检查内容质量");
        entity.setAgentId(3L);
        entity.setTriggerIntent("[\"写作\"]");
        entity.setSystemPrompt("保持审慎");
        entity.setInstructions("逐项检查");
        entity.setPriority(20);
        entity.setBuiltIn(true);
        entity.setIsGlobal(false);
        when(repository.findByCode("content-review")).thenReturn(Optional.of(entity));

        // 调用
        var result = skillStore.findByCode("content-review");

        // 断言
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().skillId()).isEqualTo(9L);
        assertThat(result.orElseThrow().name()).isEqualTo("内容审查");
        assertThat(result.orElseThrow().instructions()).isEqualTo("逐项检查");
        assertThat(result.orElseThrow().builtIn()).isTrue();
        assertThat(result.orElseThrow().global()).isFalse();
        verify(repository).findByCode("content-review");
    }

    @Test
    @DisplayName("Given 技能业务码不存在 When 读取技能 Then 返回空结果")
    void should_return_empty_when_code_not_exists() {
        // mock 方法
        when(repository.findByCode("missing")).thenReturn(Optional.empty());

        // 调用 + 断言
        assertThat(skillStore.findByCode("missing")).isEmpty();
    }
}
