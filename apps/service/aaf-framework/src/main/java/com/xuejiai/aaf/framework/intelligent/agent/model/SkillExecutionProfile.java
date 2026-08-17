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
        if (activatedSkills.isEmpty()) {
            throw new IllegalArgumentException("activatedSkills 不能为空");
        }
        if (activatedSkills.size() > selection.maxActivatedSkills()) {
            throw new IllegalArgumentException("激活 Skill 数量超出选择策略上限");
        }
        effectiveTools = List.copyOf(Objects.requireNonNull(effectiveTools, "effectiveTools 不能为空"));
    }

    /** 只在最终执行阶段拼接已激活版本正文。 */
    public String contentAppendix() {
        return activatedSkills.stream()
                .map(skill -> "## 已激活 Skill：%s\n\n%s".formatted(skill.code(), skill.content()))
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }
}
