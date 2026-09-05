package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.Collection;
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

    List<AigcProjectObject> findByProjectIdAndObjectTypeOrderByIdAsc(
            Long projectId, String objectType);

    List<AigcProjectObject> findByProjectIdAndObjectTypeAndStatusInOrderByIdAsc(
            Long projectId, String objectType, Collection<String> statuses);

    Optional<AigcProjectObject> findByProjectIdAndStableKey(Long projectId, String stableKey);

    List<AigcProjectObject> findByProjectIdAndParentIdOrderBySortOrderAscIdAsc(
            Long projectId, Long parentId);

    boolean existsByProjectIdAndParentIdAndDeletedFalse(Long projectId, Long parentId);

    @Query(
            value =
                    "select coalesce(max(instance_no), 0) from aigc_project_object where project_id = :projectId and blueprint_template_key = :templateKey",
            nativeQuery = true)
    int findHistoricalMaxInstanceNo(
            @Param("projectId") Long projectId, @Param("templateKey") String templateKey);

    @Query(
            value =
                    "select coalesce(max(instance_no), 0) from aigc_project_object where project_id = :projectId and blueprint_template_key is null and object_type = :objectType",
            nativeQuery = true)
    int findHistoricalMaxCustomInstanceNo(
            @Param("projectId") Long projectId, @Param("objectType") String objectType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select object from AigcProjectObject object where object.id = :id")
    Optional<AigcProjectObject> findLockedById(@Param("id") Long id);
}
