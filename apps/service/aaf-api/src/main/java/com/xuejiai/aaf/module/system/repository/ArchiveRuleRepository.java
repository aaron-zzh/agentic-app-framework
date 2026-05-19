package com.xuejiai.aaf.module.system.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.domain.ArchiveRule;

public interface ArchiveRuleRepository extends JpaRepository<ArchiveRule, Long> {

    List<ArchiveRule> findByEnabledTrueAndDeletedFalse();
}
