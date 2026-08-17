package com.xuejiai.aaf.framework.intelligent.core.skill;

import java.util.Objects;

/** 已激活 Skill 不可变版本的稳定引用。 */
public record SkillVersionRef(Long skillId, Long versionId, int version) {

    public SkillVersionRef {
        Objects.requireNonNull(skillId, "skillId 不能为空");
        Objects.requireNonNull(versionId, "versionId 不能为空");
        if (version < 1) {
            throw new IllegalArgumentException("Skill 版本必须大于 0");
        }
    }
}
