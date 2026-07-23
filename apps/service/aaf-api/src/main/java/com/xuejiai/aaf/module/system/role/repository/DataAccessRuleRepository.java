package com.xuejiai.aaf.module.system.role.repository;

import java.util.List;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.system.role.domain.DataAccessRule;

/**
 * @author AaronZZH & Kiro
 */
public interface DataAccessRuleRepository extends CrudEntityRepository<DataAccessRule> {

    List<DataAccessRule> findByEntitySlugAndDeletedFalse(String entitySlug);
}
