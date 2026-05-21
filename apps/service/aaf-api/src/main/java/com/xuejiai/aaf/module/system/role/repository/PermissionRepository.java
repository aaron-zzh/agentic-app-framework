package com.xuejiai.aaf.module.system.role.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.system.role.domain.Permission;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    /** 查询用户通过角色关联拥有的所有权限（按实体过滤） */
    @Query(
            """
            SELECT p FROM Permission p
            WHERE p.entitySlug = :entitySlug AND p.deleted = false
            AND p.id IN (
                SELECT rp.permissionId FROM RolePermission rp
                WHERE rp.deleted = false AND rp.roleId IN (
                    SELECT ur.roleId FROM UserRole ur
                    WHERE ur.userId = :userId AND ur.deleted = false
                )
            )
            """)
    List<Permission> findByUserIdAndEntitySlug(Long userId, String entitySlug);
}
