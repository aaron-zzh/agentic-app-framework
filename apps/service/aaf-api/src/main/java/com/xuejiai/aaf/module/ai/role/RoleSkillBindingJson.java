package com.xuejiai.aaf.module.ai.role;

import java.util.ArrayList;
import java.util.List;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;

final class RoleSkillBindingJson {

    private RoleSkillBindingJson() {}

    static String write(List<SkillBinding> bindings) {
        return JsonUtils.toJsonString(SkillBinding.copyOf(bindings, "skillBindings"));
    }

    static List<SkillBinding> read(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            var root = JsonUtils.readTreeStrict(json);
            if (!root.isArray()) {
                throw new IllegalStateException("ai_role.skill_ids 必须是 SkillBinding JSON 数组");
            }
            var result = new ArrayList<SkillBinding>();
            for (var item : root) {
                if (!item.isObject()
                        || item.size() != 2
                        || !item.path("skillKey").isString()
                        || !item.path("activationMode").isString()) {
                    throw new IllegalStateException(
                            "ai_role.skill_ids 每项必须且只能包含 skillKey/activationMode");
                }
                result.add(
                        new SkillBinding(
                                item.get("skillKey").textValue(),
                                SkillActivationMode.valueOf(
                                        item.get("activationMode").textValue())));
            }
            return SkillBinding.copyOf(result, "ai_role.skill_ids");
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                    "ai_role.skill_ids 必须是合法 SkillBinding JSON 数组", failure);
        }
    }
}
