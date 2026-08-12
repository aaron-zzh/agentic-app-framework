package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;

import jakarta.persistence.LockModeType;

public interface AigcProjectRepository extends CrudEntityRepository<AigcProject> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from AigcProject project where project.id = :id")
    Optional<AigcProject> findLockedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query(
            """
            select project from AigcProject project
            where project.id = :id and project.userId = :userId and project.deleted = false
            """)
    Optional<AigcProject> findActiveSharedLockedByIdAndUserId(
            @Param("id") Long id, @Param("userId") Long userId);
}
