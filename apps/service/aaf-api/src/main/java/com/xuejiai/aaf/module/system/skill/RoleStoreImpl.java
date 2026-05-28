package com.xuejiai.aaf.module.system.skill;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

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

    private final RoleRepository roleRepository;

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

@Repository
interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleId(String roleId);
}
