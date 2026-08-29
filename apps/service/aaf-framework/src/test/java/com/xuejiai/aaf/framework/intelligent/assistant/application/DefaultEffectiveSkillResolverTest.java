package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillVersionRef;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class DefaultEffectiveSkillResolverTest extends BaseMockitoUnitTest {

    @Mock private SkillCatalogPort skillCatalog;

    private DefaultEffectiveSkillResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultEffectiveSkillResolver(skillCatalog);
    }

    @Test
    @DisplayName("Given Assistant 授权多个 Skill When 解析命中 Skill Then 只返回请求 Skill")
    void should_resolve_only_selected_skill() {
        var selected = skill(10L, "selected");
        when(skillCatalog.findByCode("selected")).thenReturn(Optional.of(selected));

        var result = resolver.resolve(Set.of("selected", "other"), Set.of("selected"));

        assertThat(result).containsExactly(selected);
    }

    @Test
    @DisplayName("Given Skill 目录不存在命中项 When 解析 Then 拒绝未发布技能")
    void should_reject_missing_selected_skill() {
        when(skillCatalog.findByCode("selected")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(Set.of("selected"), Set.of("selected")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在 APPROVED Skill");
    }

    @Test
    @DisplayName("Given Skill 不属于 Assistant 授权候选 When 解析 Then 拒绝越界加载")
    void should_reject_skill_outside_authorized_candidates() {
        assertThatThrownBy(() -> resolver.resolve(Set.of("selected"), Set.of("other")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不属于 Assistant 当前候选范围");
    }

    private SkillDef skill(Long id, String code) {
        return new SkillDef(
                id,
                code,
                code,
                code + "描述",
                new SkillVersionRef(id, id, 1),
                code + "提示词",
                Set.of(),
                Set.of(),
                false,
                false);
    }
}
