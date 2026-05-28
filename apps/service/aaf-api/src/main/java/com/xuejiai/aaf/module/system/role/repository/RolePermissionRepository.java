package com.xuejiai.aaf.module.system.role.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.role.domain.RolePermission;

/**
 * @author AaronZZH & Kiro
 */
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRoleIdAndDeletedFalse(Long roleId);

    void deleteByRoleId(Long roleId);
}
