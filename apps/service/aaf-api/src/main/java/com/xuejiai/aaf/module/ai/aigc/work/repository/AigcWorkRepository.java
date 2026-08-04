package com.xuejiai.aaf.module.ai.aigc.work.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.work.domain.AigcWork;

import jakarta.persistence.LockModeType;

public interface AigcWorkRepository extends CrudEntityRepository<AigcWork> {

    List<AigcWork> findByProjectIdAndStatusNot(Long projectId, String status);

    Optional<AigcWork> findByDeliverableObjectIdAndAdoptedObjectVersionId(
            Long deliverableObjectId, Long adoptedObjectVersionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select work from AigcWork work where work.id = :id")
    Optional<AigcWork> findLockedById(@Param("id") Long id);
}
