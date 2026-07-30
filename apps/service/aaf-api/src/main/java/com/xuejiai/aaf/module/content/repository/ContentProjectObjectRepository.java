package com.xuejiai.aaf.module.content.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.content.domain.ContentProjectObject;

import jakarta.persistence.LockModeType;

/**
 * 项目对象仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface ContentProjectObjectRepository extends CrudEntityRepository<ContentProjectObject> {

    java.util.List<ContentProjectObject> findByProjectIdOrderBySortOrderAscIdAsc(Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select object from ContentProjectObject object where object.id = :id")
    Optional<ContentProjectObject> findLockedById(@Param("id") Long id);
}
