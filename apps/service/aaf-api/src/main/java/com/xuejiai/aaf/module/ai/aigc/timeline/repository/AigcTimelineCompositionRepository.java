package com.xuejiai.aaf.module.ai.aigc.timeline.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.timeline.domain.AigcTimelineComposition;

import jakarta.persistence.LockModeType;

public interface AigcTimelineCompositionRepository
        extends CrudEntityRepository<AigcTimelineComposition> {

    List<AigcTimelineComposition> findByProjectId(Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select composition from AigcTimelineComposition composition where composition.id = :id")
    Optional<AigcTimelineComposition> findLockedById(@Param("id") Long id);
}
