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

import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizedSkillSummary;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillScope;
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
    @DisplayName("Given 冻结 Skill 版本 When 解析命中版本 Then 返回该不可变版本")
    void should_resolve_frozen_skill_version() {
        var selected = skill(10L, "selected", 1);
        when(skillCatalog.findByVersion(selected.version())).thenReturn(Optional.of(selected));

        var result = resolver.resolve(List.of(summary(selected)));

        assertThat(result).containsExactly(selected);
    }

    @Test
    @DisplayName("Given 冻结 Skill 版本不存在 When 解析 Then 拒绝加载")
    void should_reject_missing_frozen_skill_version() {
        var selected = skill(10L, "selected", 1);
        when(skillCatalog.findByVersion(selected.version())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(List.of(summary(selected))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在 APPROVED SkillVersion");
    }

    @Test
    @DisplayName("Given 目录返回不同 Skill 版本 When 解析 Then 拒绝版本漂移")
    void should_reject_version_drift_when_resolving_frozen_skill() {
        var selected = skill(10L, "selected", 1);
        var drifted = skill(10L, "selected", 2);
        when(skillCatalog.findByVersion(selected.version())).thenReturn(Optional.of(drifted));

        assertThatThrownBy(() -> resolver.resolve(List.of(summary(selected))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在 APPROVED SkillVersion");
    }

    private static AuthorizedSkillSummary summary(SkillDef skill) {
        return new AuthorizedSkillSummary(
                skill.code(),
                skill.name(),
                skill.summary(),
                SkillScope.ROLE,
                SkillActivationMode.ON_DEMAND,
                skill.version(),
                List.of(),
                skill.requiredModelCapabilities(),
                skill.requiredToolNames());
    }

    private static SkillDef skill(Long id, String code, int version) {
        return new SkillDef(
                id,
                code,
                code,
                code + "描述",
                new SkillVersionRef(id, id + version, version),
                code + "提示词",
                Set.of(),
                Set.of(),
                false,
                false);
    }
}
