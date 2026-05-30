package com.xuejiai.aaf.module.ai.skill;

import java.util.List;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.role.RoleStore;

import lombok.RequiredArgsConstructor;

/**
 * RoleStore 实现——从数据库获取 Role 的技能和工具配置。
 *
 * @author AaronZZH & Kiro
 */
@Component
@RequiredArgsConstructor
public class RoleStoreImpl implements RoleStore {

    private final AiRoleRepository roleRepository;

    @Override
    public List<String> getSkillIds(String roleId) {
        return roleRepository
                .findByRoleId(roleId)
                .map(role -> parseJsonArray(role.getSkillIds()))
                .orElse(List.of());
    }

    @Override
    public List<String> getToolWhitelist(String roleId) {
        return roleRepository
                .findByRoleId(roleId)
                .map(role -> parseJsonArray(role.getToolWhitelist()))
                .orElse(List.of());
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        return List.of(json.replaceAll("[\\[\\]\"]", "").split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
