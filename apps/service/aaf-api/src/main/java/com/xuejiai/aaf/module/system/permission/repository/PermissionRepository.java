package com.xuejiai.aaf.module.system.permission.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.permission.domain.Permission;

/**
 * 权限点仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findByDeletedFalseOrderBySortOrder();

    boolean existsByParentIdAndDeletedFalse(Long parentId);

    boolean existsByCodeAndDeletedFalse(String code);

    Optional<Permission> findByIdAndDeletedFalse(Long id);

    List<Permission> findByIdInAndDeletedFalse(List<Long> ids);
}
