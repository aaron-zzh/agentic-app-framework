package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Assistant 可复用的角色定义，描述职责、技能和工具边界。 */
public record Role(
        String key,
        String name,
        List<String> responsibilities,
        List<String> nonResponsibilities,
        Set<String> skillKeys,
        Set<String> toolKeys) {

    public Role {
        key = requireText(key, "Role key");
        name = requireText(name, "Role name");
        responsibilities = copyTexts(responsibilities, "responsibilities");
        nonResponsibilities = copyTexts(nonResponsibilities, "nonResponsibilities");
        skillKeys = copyTextSet(skillKeys, "skillKeys");
        toolKeys = copyTextSet(toolKeys, "toolKeys");
        if (responsibilities.isEmpty()) {
            throw new IllegalArgumentException("Role responsibilities 不能为空");
        }
    }

    private static List<String> copyTexts(List<String> values, String name) {
        Objects.requireNonNull(values, name + " 不能为空");
        var copy = values.stream().map(value -> requireText(value, name)).toList();
        if (copy.size() != Set.copyOf(copy).size()) {
            throw new IllegalArgumentException(name + " 不能重复");
        }
        return copy;
    }

    private static Set<String> copyTextSet(Set<String> values, String name) {
        Objects.requireNonNull(values, name + " 不能为空");
        values.forEach(value -> requireText(value, name));
        return Set.copyOf(values);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空白");
        }
        return value;
    }
}
