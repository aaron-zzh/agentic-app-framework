package com.xuejiai.aaf.framework.intelligent.infrastructure.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.engine.skill.SkillStore;
import com.xuejiai.aaf.framework.engine.skill.SkillStore.SkillRecord;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillVersionRef;
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
        var record = skill(7L, "content-review", false, Set.of("contentReview"));
        when(skillStore.findByCode("content-review")).thenReturn(Optional.of(record));

        // 调用
        var result = adapter.findByCode("content-review");

        // 断言
        assertThat(result)
                .contains(
                        new SkillDef(
                                7L,
                                "content-review",
                                "内容审查",
                                "检查内容质量",
                                new SkillVersionRef(7L, 3L, 2),
                                "逐项检查",
                                Set.of("contentReview"),
                                Set.of("TEXT"),
                                false,
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
        var first = skill(1L, "support.read", true, Set.of());
        var second = skill(2L, "content-review", true, Set.of("contentReview"));
        when(skillStore.findBuiltIn()).thenReturn(List.of(first, second));

        // 调用
        var result = adapter.findBuiltIn();

        // 断言
        assertThat(result).extracting(SkillDef::skillId).containsExactly(1L, 2L);
        assertThat(result)
                .extracting(SkillDef::code)
                .containsExactly("support.read", "content-review");
        assertThat(result.getFirst().requiredToolNames()).isEmpty();
        assertThat(result.get(1).requiredToolNames()).containsExactly("contentReview");
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

    private SkillRecord skill(
            Long id, String code, boolean builtIn, Set<String> requiredToolNames) {
        return new SkillRecord(
                id,
                code,
                "内容审查",
                "检查内容质量",
                3L,
                2,
                "逐项检查",
                requiredToolNames,
                Set.of("TEXT"),
                builtIn,
                false);
    }
}
