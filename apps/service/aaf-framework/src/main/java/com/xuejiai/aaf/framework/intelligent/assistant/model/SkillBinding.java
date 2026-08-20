package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/** Skill 与激活模式的稳定绑定。 */
public record SkillBinding(String skillKey, SkillActivationMode activationMode) {

    public SkillBinding {
        skillKey = requireText(skillKey);
        Objects.requireNonNull(activationMode, "activationMode 不能为空");
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("SkillBinding 不支持字段: " + fieldName);
    }

    public static List<SkillBinding> copyOf(List<SkillBinding> bindings, String field) {
        var copy = List.copyOf(Objects.requireNonNull(bindings, field + " 不能为空"));
        var keys = new LinkedHashSet<String>();
        for (var binding : copy) {
            Objects.requireNonNull(binding, field + " 不能包含 null");
            if (!keys.add(binding.skillKey())) {
                throw new IllegalArgumentException(field + " 不能重复绑定 Skill: " + binding.skillKey());
            }
        }
        return copy;
    }

    public static Set<String> skillKeys(List<SkillBinding> bindings) {
        var keys = new LinkedHashSet<String>();
        bindings.forEach(binding -> keys.add(binding.skillKey()));
        return java.util.Collections.unmodifiableSet(keys);
    }

    public static Set<String> skillKeys(
            List<SkillBinding> bindings, SkillActivationMode activationMode) {
        var keys = new LinkedHashSet<String>();
        bindings.stream()
                .filter(binding -> binding.activationMode() == activationMode)
                .forEach(binding -> keys.add(binding.skillKey()));
        return java.util.Collections.unmodifiableSet(keys);
    }

    private static String requireText(String value) {
        Objects.requireNonNull(value, "skillKey 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException("skillKey 不能为空白");
        }
        return value.trim();
    }
}
