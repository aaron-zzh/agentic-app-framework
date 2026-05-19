package com.xuejiai.aaf.module.system.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.domain.DataAccessRule;

public interface DataAccessRuleRepository extends JpaRepository<DataAccessRule, Long> {

    List<DataAccessRule> findByEntitySlugAndDeletedFalse(String entitySlug);
}
