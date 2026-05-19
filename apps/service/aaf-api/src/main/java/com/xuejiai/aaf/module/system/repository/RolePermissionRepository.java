package com.xuejiai.aaf.module.system.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.domain.RolePermission;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRoleIdAndDeletedFalse(Long roleId);

    void deleteByRoleId(Long roleId);
}
