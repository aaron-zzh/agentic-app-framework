package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectObject;

import jakarta.persistence.LockModeType;

public interface AigcProjectObjectRepository extends CrudEntityRepository<AigcProjectObject> {

    List<AigcProjectObject> findByProjectIdOrderBySortOrderAscIdAsc(Long projectId);

    Optional<AigcProjectObject> findByProjectIdAndObjectKey(Long projectId, String objectKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select object from AigcProjectObject object where object.id = :id")
    Optional<AigcProjectObject> findLockedById(@Param("id") Long id);
}
