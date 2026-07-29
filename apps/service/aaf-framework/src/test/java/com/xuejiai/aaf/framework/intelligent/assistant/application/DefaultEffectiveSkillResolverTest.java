package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    @DisplayName("Given 未分配角色 When 解析有效技能 Then 返回全部内置通用技能")
    void should_return_built_in_skills_when_roles_are_empty() {
        // 准备参数
        var first = skill(1L, "通用总结", true);
        var second = skill(2L, "通用检索", true);
        when(skillCatalog.findBuiltIn()).thenReturn(List.of(first, second));

        // 调用
        var result = resolver.resolve(List.of());

        // 断言
        assertThat(result).containsExactly(first, second);
        verify(skillCatalog).findBuiltIn();
    }

    @Test
    @DisplayName("Given 多个角色包含重复技能 When 解析有效技能 Then 合并并按技能标识去重")
    void should_merge_and_deduplicate_skills_from_multiple_roles() {
        // 准备参数
        var shared = skill(10L, "共享技能", false);
        var unique = skill(11L, "专属技能", false);
        var firstRole = role("first", Set.of("shared"));
        var secondRole = role("second", Set.of("shared", "unique"));
        when(skillCatalog.findBuiltIn()).thenReturn(List.of());
        when(skillCatalog.findByCode("shared")).thenReturn(Optional.of(shared));
        when(skillCatalog.findByCode("unique")).thenReturn(Optional.of(unique));

        // 调用
        var result = resolver.resolve(List.of(firstRole, secondRole));

        // 断言
        assertThat(result).containsExactlyInAnyOrder(shared, unique);
        assertThat(result).hasSize(2);
        verify(skillCatalog, times(2)).findByCode("shared");
        verify(skillCatalog).findByCode("unique");
    }

    @Test
    @DisplayName("Given 角色技能与内置技能标识相同 When 解析有效技能 Then 角色技能覆盖内置定义")
    void should_prefer_role_skill_when_identifier_conflicts() {
        // 准备参数
        var builtIn = skill(20L, "内容审查-内置", true);
        var roleSkill = skill(20L, "内容审查-角色", false);
        when(skillCatalog.findBuiltIn()).thenReturn(List.of(builtIn));
        when(skillCatalog.findByCode("content-review")).thenReturn(Optional.of(roleSkill));

        // 调用
        var result = resolver.resolve(List.of(role("reviewer", Set.of("content-review"))));

        // 断言
        assertThat(result).containsExactly(roleSkill);
        assertThat(result).doesNotContain(builtIn);
        verify(skillCatalog).findByCode("content-review");
    }

    private Role role(String key, Set<String> skillKeys) {
        return new Role(key, key, List.of("执行指定职责"), List.of(), skillKeys, Set.of());
    }

    private SkillDef skill(Long id, String name, boolean builtIn) {
        return new SkillDef(id, name, name + "描述", null, List.of(), name + "提示词", 10, builtIn);
    }
}
