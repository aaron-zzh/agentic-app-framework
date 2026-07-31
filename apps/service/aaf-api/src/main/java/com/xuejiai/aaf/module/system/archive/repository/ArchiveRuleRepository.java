package com.xuejiai.aaf.module.system.archive.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.archive.domain.ArchiveRule;

/**
 * @author AaronZZH & Kiro
 */
public interface ArchiveRuleRepository extends JpaRepository<ArchiveRule, Long> {

    List<ArchiveRule> findByEnabledTrueAndDeletedFalse();
}
