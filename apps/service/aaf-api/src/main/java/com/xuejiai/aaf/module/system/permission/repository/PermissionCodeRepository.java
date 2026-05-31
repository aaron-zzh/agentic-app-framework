package com.xuejiai.aaf.module.system.permission.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.permission.domain.PermissionCode;

/** 权限码仓储。 */
public interface PermissionCodeRepository extends JpaRepository<PermissionCode, Long> {

    List<PermissionCode> findByDeletedFalseOrderByModuleAscResourceAscActionAsc();

    boolean existsByCodeAndDeletedFalse(String code);

    Optional<PermissionCode> findByCodeAndDeletedFalseAndStatus(String code, Integer status);

    Optional<PermissionCode> findByIdAndDeletedFalse(Long id);

    List<PermissionCode> findByIdInAndDeletedFalse(List<Long> ids);
}
