package com.xuejiai.aaf.module.system.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.domain.User;

/** 用户数据访问层。Hibernate @SoftDelete 自动过滤已删除记录。 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
