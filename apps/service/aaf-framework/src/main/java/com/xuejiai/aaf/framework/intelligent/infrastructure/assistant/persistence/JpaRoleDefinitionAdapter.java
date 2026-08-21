package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;
import com.xuejiai.aaf.framework.intelligent.assistant.port.RoleDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;

/** 将 ai_role 持久化模型映射为只读领域角色。 */
public final class JpaRoleDefinitionAdapter implements RoleDefinitionPort {

    private final AiRoleRepository repository;

    public JpaRoleDefinitionAdapter(AiRoleRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    @Override
    public Optional<Role> findByCode(String roleCode) {
        Objects.requireNonNull(roleCode, "roleCode 不能为空");
        if (roleCode.isBlank()) {
            throw new IllegalArgumentException("roleCode 不能为空白");
        }
        return repository.findByCode(roleCode).map(this::toDomain);
    }

    private Role toDomain(com.xuejiai.aaf.framework.intelligent.assistant.role.Role entity) {
        return new Role(
                entity.getCode(),
                entity.getName(),
                responsibilities(entity.getDescription(), entity.getCode()),
                List.of(),
                parseBindings(entity.getSkillIds(), "ai_role.skill_ids"),
                parseKeys(entity.getToolWhitelist(), "toolWhitelist"));
    }

    private List<SkillBinding> parseBindings(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            var root = JsonUtils.readTreeStrict(json);
            if (!root.isArray()) {
                throw new IllegalStateException(fieldName + " 必须是 SkillBinding JSON 数组");
            }
            var result = new java.util.ArrayList<SkillBinding>();
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
                                item.get("skillKey").textValue(),
                                SkillActivationMode.valueOf(
                                        item.get("activationMode").textValue())));
            }
            return SkillBinding.copyOf(result, fieldName);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException failure) {
            throw new IllegalStateException(fieldName + " 必须是合法 SkillBinding JSON 数组", failure);
        }
    }

    private Set<String> parseKeys(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            var node = JsonUtils.readTreeStrict(json);
            if (!node.isArray()) {
                throw new IllegalStateException(fieldName + " 必须是 JSON 字符串数组");
            }
            for (var item : node) {
                if (!item.isString()) {
                    throw new IllegalStateException(fieldName + " 必须是 JSON 字符串数组");
                }
            }
            return Set.copyOf(JsonUtils.parseArray(json, String.class));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException(fieldName + " 必须是合法 JSON 字符串数组", exception);
        }
    }

    private List<String> responsibilities(String description, String roleCode) {
        if (description == null || description.isBlank()) {
            throw new IllegalStateException("角色 %s 缺少职责描述".formatted(roleCode));
        }
        return List.of(description);
    }
}
