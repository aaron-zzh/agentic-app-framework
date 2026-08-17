package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef;

/** 为不经过 Assistant 路由的受控系统入口构建固定 Skill 执行画像。 */
public final class FixedSkillExecutionProfile {

    private FixedSkillExecutionProfile() {}

    public static SkillExecutionProfile from(SkillDef skill, List<ToolRef> effectiveTools) {
        Objects.requireNonNull(skill, "skill 不能为空");
        var summary =
                new AuthorizedSkillSummary(
                        skill.code(),
                        skill.name(),
                        skill.summary(),
                        List.of(),
                        skill.requiredModelCapabilities(),
                        skill.requiredToolNames());
        var selection =
                new SkillSelectionManifest(
                        "system:" + skill.code(),
                        skill.code(),
                        SkillSelectionMode.FIXED,
                        1,
                        List.of(summary));
        var activated =
                new ActivatedSkill(
                        skill.code(),
                        skill.version(),
                        skill.content(),
                        skill.requiredToolNames(),
                        skill.requiredModelCapabilities(),
                        List.of(),
                        List.of());
        return new SkillExecutionProfile(selection, List.of(activated), effectiveTools);
    }
}
