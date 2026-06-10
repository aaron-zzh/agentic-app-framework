package com.xuejiai.aaf.module.ai.skill;

import java.util.List;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRole;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
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
    private final AiAssistantRoleRepository assistantRoleRepository;

    @Override
    public List<Long> getSkillIds(Long roleId) {
        return roleRepository
                .findById(roleId)
                .map(role -> parseJsonArrayAsLong(role.getSkillIds()))
                .orElse(List.of());
    }

    @Override
    public List<String> getToolWhitelist(Long roleId) {
        return roleRepository
                .findById(roleId)
                .map(role -> parseJsonArray(role.getToolWhitelist()))
                .orElse(List.of());
    }

    @Override
    public List<Long> getRoleIdsByAssistant(Long assistantId) {
        if (assistantId == null) return List.of();
        return assistantRoleRepository.findByAssistantIdOrderBySortOrderAsc(assistantId).stream()
                .map(AiAssistantRole::getRoleId)
                .toList();
    }

    private List<Long> parseJsonArrayAsLong(String json) {
        if (json == null || json.isBlank()) return List.of();
        return List.of(json.replaceAll("[\\[\\]\"]", "").split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(
                        s -> {
                            try {
                                return Long.parseLong(s);
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        return List.of(json.replaceAll("[\\[\\]\"]", "").split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
