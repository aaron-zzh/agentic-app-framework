package com.xuejiai.aaf.module.content.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.content.domain.ContentProject;

import jakarta.persistence.LockModeType;

/**
 * 内容项目仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface ContentProjectRepository extends CrudEntityRepository<ContentProject> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from ContentProject project where project.id = :id")
    Optional<ContentProject> findLockedById(@Param("id") Long id);
}
