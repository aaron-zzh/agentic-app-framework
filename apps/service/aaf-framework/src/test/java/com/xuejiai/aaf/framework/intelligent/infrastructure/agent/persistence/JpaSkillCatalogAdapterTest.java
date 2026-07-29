package com.xuejiai.aaf.framework.intelligent.infrastructure.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.engine.skill.SkillStore;
import com.xuejiai.aaf.framework.engine.skill.SkillStore.SkillRecord;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class JpaSkillCatalogAdapterTest extends BaseMockitoUnitTest {

    @Mock private SkillStore skillStore;

    private JpaSkillCatalogAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaSkillCatalogAdapter(skillStore);
    }

    @Test
    @DisplayName("Given 技能业务码存在 When 查询技能 Then 委托既有存储并映射领域定义")
    void should_delegate_and_map_when_code_exists() {
        // 准备参数
        var record = skill(7L, false, "[\"写作\",\"review\"]");
        when(skillStore.findByCode("content-review")).thenReturn(Optional.of(record));

        // 调用
        var result = adapter.findByCode("content-review");

        // 断言
        assertThat(result)
                .contains(
                        new SkillDef(
                                7L,
                                "内容审查",
                                "检查内容质量",
                                3L,
                                List.of("写作", "review"),
                                "保持审慎",
                                20,
                                false));
        verify(skillStore).findByCode("content-review");
    }

    @Test
    @DisplayName("Given 技能业务码不存在 When 查询技能 Then 返回空结果")
    void should_return_empty_when_code_not_exists() {
        // mock 方法
        when(skillStore.findByCode("missing")).thenReturn(Optional.empty());

        // 调用 + 断言
        assertThat(adapter.findByCode("missing")).isEmpty();
    }

    @Test
    @DisplayName("Given 存在活跃内置技能 When 查询内置目录 Then 保持存储顺序并完整映射")
    void should_map_built_in_skills() {
        // 准备参数
        var first = skill(1L, true, null);
        var second = skill(2L, true, "[\"总结\"]");
        when(skillStore.findBuiltIn()).thenReturn(List.of(first, second));

        // 调用
        var result = adapter.findBuiltIn();

        // 断言
        assertThat(result).extracting(SkillDef::skillId).containsExactly(1L, 2L);
        assertThat(result.getFirst().triggerKeywords()).isEmpty();
        assertThat(result.get(1).triggerKeywords()).containsExactly("总结");
        assertThat(result).allMatch(SkillDef::builtIn);
        verify(skillStore).findBuiltIn();
    }

    @Test
    @DisplayName("Given 技能业务码为空白 When 查询技能 Then 拒绝无效查询")
    void should_reject_blank_skill_code() {
        assertThatThrownBy(() -> adapter.findByCode(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skillCode 不能为空白");
    }

    private SkillRecord skill(Long id, boolean builtIn, String triggerIntent) {
        return new SkillRecord(
                id, "内容审查", "检查内容质量", 3L, triggerIntent, "保持审慎", "逐项检查", 20, builtIn, false);
    }
}
