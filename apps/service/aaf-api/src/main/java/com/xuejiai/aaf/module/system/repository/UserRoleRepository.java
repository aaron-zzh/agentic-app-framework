package com.xuejiai.aaf.module.system.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.domain.UserRole;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserIdAndDeletedFalse(Long userId);
}
