package com.xuejiai.aaf.module.system.role.repository;

import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.system.role.domain.Role;

/**
 * @author AaronZZH & Kiro
 */
public interface RoleRepository extends CrudEntityRepository<Role> {

    Optional<Role> findByCodeAndDeletedFalse(String code);

    boolean existsByCodeAndDeletedFalse(String code);
}
