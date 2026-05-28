package com.xuejiai.aaf.module.system.workflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.workflow.domain.ArchiveRule;

/**
 * @author AaronZZH & Kiro
 */
public interface ArchiveRuleRepository extends JpaRepository<ArchiveRule, Long> {

    List<ArchiveRule> findByEnabledTrueAndDeletedFalse();
}
