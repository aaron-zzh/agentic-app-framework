package com.xuejiai.aaf.module.system.entity.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.entity.domain.EntityDef;

/**
 * 实体定义仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface EntityDefRepository extends JpaRepository<EntityDef, Long> {

    Optional<EntityDef> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
