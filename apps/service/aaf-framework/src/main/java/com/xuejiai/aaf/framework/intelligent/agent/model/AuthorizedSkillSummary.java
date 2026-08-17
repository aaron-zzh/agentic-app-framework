package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 选择阶段唯一可见的 Skill 元数据，禁止包含正文或业务工具句柄。 */
public record AuthorizedSkillSummary(
        String code,
        String name,
        String summary,
        List<String> categories,
        Set<String> modelCapabilities,
        Set<String> toolPurposeTags) {

    public AuthorizedSkillSummary {
        code = requireText(code, "code");
        name = requireText(name, "name");
        summary = requireText(summary, "summary");
        categories = List.copyOf(Objects.requireNonNull(categories, "categories 不能为空"));
        modelCapabilities =
                Set.copyOf(Objects.requireNonNull(modelCapabilities, "modelCapabilities 不能为空"));
        toolPurposeTags =
                Set.copyOf(Objects.requireNonNull(toolPurposeTags, "toolPurposeTags 不能为空"));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }
}
