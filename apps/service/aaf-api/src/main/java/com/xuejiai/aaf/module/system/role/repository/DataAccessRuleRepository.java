package com.xuejiai.aaf.module.system.role.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.role.domain.DataAccessRule;

public interface DataAccessRuleRepository extends JpaRepository<DataAccessRule, Long> {

    List<DataAccessRule> findByEntitySlugAndDeletedFalse(String entitySlug);
}
