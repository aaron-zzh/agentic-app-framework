package com.xuejiai.aaf.module.ai.skill;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRole;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.RoleStore;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RoleStoreImpl implements RoleStore {

    private final AiRoleRepository roleRepository;
    private final AiAssistantRoleRepository assistantRoleRepository;

    @Override
    public List<String> getSkillCodes(Long roleId) {
        return roleRepository
                .findById(roleId)
                .map(role -> parseBindings(role.getSkillIds(), "ai_role.skill_ids"))
                .orElse(List.of())
                .stream()
                .map(SkillBinding::skillKey)
                .toList();
    }

    @Override
    public List<String> getToolWhitelist(Long roleId) {
        return roleRepository
                .findById(roleId)
                .map(role -> parseStringArray(role.getToolWhitelist(), "ai_role.tool_whitelist"))
                .orElse(List.of());
    }

    @Override
    public List<Long> getRoleIdsByAssistant(Long assistantId) {
        if (assistantId == null) return List.of();
        return assistantRoleRepository
                .findByAssistantIdAndEnabledTrueOrderBySortOrderAsc(assistantId)
                .stream()
                .map(AiAssistantRole::getRoleId)
                .toList();
    }

    private static List<SkillBinding> parseBindings(String json, String fieldName) {
        if (json == null || json.isBlank()) return List.of();
        try {
            var root = JsonUtils.readTreeStrict(json);
            if (!root.isArray()) {
                throw new IllegalStateException(fieldName + " 必须是 SkillBinding JSON 数组");
            }
            var result = new ArrayList<SkillBinding>();
            for (var item : root) {
                if (!item.isObject()
                        || item.size() != 2
                        || !item.path("skillKey").isString()
                        || !item.path("activationMode").isString()) {
                    throw new IllegalStateException(
                            fieldName + " 每项必须且只能包含 skillKey/activationMode");
                }
                result.add(
                        new SkillBinding(
                                item.get("skillKey").asString(),
                                SkillActivationMode.valueOf(
                                        item.get("activationMode").asString())));
            }
            return SkillBinding.copyOf(result, fieldName);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException failure) {
            throw new IllegalStateException(fieldName + " 必须是合法 SkillBinding JSON 数组", failure);
        }
    }

    private static List<String> parseStringArray(String json, String fieldName) {
        if (json == null || json.isBlank()) return List.of();
        try {
            var root = JsonUtils.readTreeStrict(json);
            if (!root.isArray()) {
                throw new IllegalStateException(fieldName + " 必须是 JSON 字符串数组");
            }
            var values = new ArrayList<String>();
            for (var item : root) {
                if (!item.isString()) {
                    throw new IllegalStateException(fieldName + " 必须是 JSON 字符串数组");
                }
                values.add(item.asString());
            }
            return List.copyOf(values);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException failure) {
            throw new IllegalStateException(fieldName + " 必须是合法 JSON 字符串数组", failure);
        }
    }
}
