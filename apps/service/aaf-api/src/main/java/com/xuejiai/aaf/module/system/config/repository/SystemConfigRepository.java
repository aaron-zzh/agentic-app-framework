package com.xuejiai.aaf.module.system.config.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.config.domain.SystemConfig;

/**
 * @author AaronZZH & Kiro
 */
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    Optional<SystemConfig> findByConfigKeyAndDeletedFalse(String configKey);

    List<SystemConfig> findByCategoryAndDeletedFalse(String category);
}
