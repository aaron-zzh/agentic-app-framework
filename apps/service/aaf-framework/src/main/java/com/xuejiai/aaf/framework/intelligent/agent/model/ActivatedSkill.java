package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillScope;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillVersionRef;

/** AAF 校验后可进入执行阶段的已激活 Skill。 */
public record ActivatedSkill(
        String code,
        SkillVersionRef version,
        SkillScope scope,
        SkillActivationMode activationMode,
        String content,
        Set<String> requiredToolNames,
        Set<String> requiredModelCapabilities,
        List<String> referenceKeys,
        List<Long> knowledgeBindingIds,
        boolean inheritRoleTools) {

    public ActivatedSkill {
        code = requireText(code, "code");
        Objects.requireNonNull(version, "version 不能为空");
        Objects.requireNonNull(scope, "scope 不能为空");
        Objects.requireNonNull(activationMode, "activationMode 不能为空");
        content = requireText(content, "content");
        requiredToolNames =
                Set.copyOf(Objects.requireNonNull(requiredToolNames, "requiredToolNames 不能为空"));
        requiredModelCapabilities =
                Set.copyOf(
                        Objects.requireNonNull(
                                requiredModelCapabilities, "requiredModelCapabilities 不能为空"));
        referenceKeys = List.copyOf(Objects.requireNonNull(referenceKeys, "referenceKeys 不能为空"));
        knowledgeBindingIds =
                List.copyOf(
                        Objects.requireNonNull(knowledgeBindingIds, "knowledgeBindingIds 不能为空"));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }
}
