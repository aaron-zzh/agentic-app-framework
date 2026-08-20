package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.List;
import java.util.Objects;

/** 已选择、校验和编译完成的任务级 Skill 执行画像。 */
public record SkillExecutionProfile(
        SkillSelectionManifest selection,
        List<ActivatedSkill> activatedSkills,
        List<ToolRef> effectiveTools) {

    public SkillExecutionProfile {
        Objects.requireNonNull(selection, "selection 不能为空");
        activatedSkills =
                List.copyOf(Objects.requireNonNull(activatedSkills, "activatedSkills 不能为空"));
        effectiveTools = List.copyOf(Objects.requireNonNull(effectiveTools, "effectiveTools 不能为空"));
    }
}
