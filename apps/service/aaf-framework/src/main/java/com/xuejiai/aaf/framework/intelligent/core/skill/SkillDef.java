package com.xuejiai.aaf.framework.intelligent.core.skill;

import java.util.Objects;
import java.util.Set;

/** AAF 管理的当前可执行 Skill 版本投影。 */
public record SkillDef(
        Long skillId,
        String code,
        String name,
        String summary,
        SkillVersionRef version,
        String content,
        Set<String> requiredToolNames,
        Set<String> requiredModelCapabilities,
        boolean builtIn) {

    public SkillDef {
        Objects.requireNonNull(skillId, "skillId 不能为空");
        code = requireText(code, "code");
        name = requireText(name, "name");
        summary = requireText(summary, "summary");
        Objects.requireNonNull(version, "version 不能为空");
        content = requireText(content, "content");
        requiredToolNames =
                Set.copyOf(Objects.requireNonNull(requiredToolNames, "requiredToolNames 不能为空"));
        requiredModelCapabilities =
                Set.copyOf(
                        Objects.requireNonNull(
                                requiredModelCapabilities, "requiredModelCapabilities 不能为空"));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }
}
