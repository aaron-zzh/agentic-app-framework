package com.xuejiai.aaf.module.system.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.domain.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCodeAndDeletedFalse(String code);

    boolean existsByCodeAndDeletedFalse(String code);
}
