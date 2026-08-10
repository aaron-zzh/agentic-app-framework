package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
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
                parseKeys(entity.getSkillIds(), "skillIds"),
                parseKeys(entity.getToolWhitelist(), "toolWhitelist"));
    }

    private Set<String> parseKeys(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            var node = JsonUtils.readTree(json);
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
