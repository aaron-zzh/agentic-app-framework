package com.xuejiai.aaf.module.system.dict.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.dict.domain.DictType;

/**
 * @author AaronZZH & Kiro
 */
public interface DictTypeRepository
        extends JpaRepository<DictType, Long>, JpaSpecificationExecutor<DictType> {

    boolean existsByTypeAndDeletedFalse(String type);

    boolean existsByNameAndDeletedFalse(String name);

    Optional<DictType> findByTypeAndDeletedFalse(String type);
}
