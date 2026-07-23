package com.xuejiai.aaf.module.system.dict.repository;

import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.system.dict.domain.DictType;

/**
 * @author AaronZZH & Kiro
 */
public interface DictTypeRepository extends CrudEntityRepository<DictType> {

    boolean existsByTypeAndDeletedFalse(String type);

    boolean existsByNameAndDeletedFalse(String name);

    Optional<DictType> findByTypeAndDeletedFalse(String type);
}
