package com.xuejiai.aaf.framework.intelligent.core.skill;

import java.util.Objects;
import java.util.Set;

/** Skill 选择阶段可见的最小摘要，不包含 content 或关联正文。 */
public record SkillSummary(
        Long skillId, String code, String name, String summary, Set<String> categories) {
    public SkillSummary {
        code = requireText(code, "code");
        name = requireText(name, "name");
        summary = summary == null ? "" : summary.trim();
        categories = Set.copyOf(Objects.requireNonNull(categories, "categories 不能为空"));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }
}
