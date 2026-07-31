package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class DefaultEffectiveSkillResolverTest extends BaseMockitoUnitTest {

    @Mock private SkillCatalogPort skillCatalog;

    private DefaultEffectiveSkillResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultEffectiveSkillResolver(skillCatalog);
    }

    @Test
    @DisplayName("Given Role 包含多个 Skill When 解析命中 Skill Then 只返回该 Skill")
    void should_resolve_only_selected_skill() {
        var selected = skill(10L, "selected");
        var role = role("writer", Set.of("selected", "other"));
        when(skillCatalog.findByCode("selected")).thenReturn(Optional.of(selected));

        var result = resolver.resolve(role, "selected");

        assertThat(result).containsExactly(selected);
    }

    @Test
    @DisplayName("Given Skill 目录不存在命中项 When 解析 Then 返回空列表")
    void should_return_empty_when_selected_skill_is_missing() {
        var role = role("writer", Set.of("selected"));
        when(skillCatalog.findByCode("selected")).thenReturn(Optional.empty());

        var result = resolver.resolve(role, "selected");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Given Skill 不属于有效 Role When 解析 Then 拒绝越界加载")
    void should_reject_skill_outside_effective_role() {
        var role = role("writer", Set.of("selected"));

        assertThatThrownBy(() -> resolver.resolve(role, "other"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不属于当前有效 Role");
    }

    private Role role(String key, Set<String> skillKeys) {
        return new Role(key, key, List.of("执行指定职责"), List.of(), skillKeys, Set.of());
    }

    private SkillDef skill(Long id, String name) {
        return new SkillDef(id, name, name + "描述", null, List.of(), name + "提示词", 10, false);
    }
}
