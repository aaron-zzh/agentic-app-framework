package com.xuejiai.aaf.module.elder.profile.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.elder.profile.domain.MedicationRecord;

/** 服药记录数据访问层。 */
public interface MedicationRecordRepository extends CrudEntityRepository<MedicationRecord> {

    Optional<MedicationRecord> findByPlanIdAndScheduledAt(Long planId, LocalDateTime scheduledAt);

    List<MedicationRecord> findTop100ByPlanIdAndDeletedFalseOrderByScheduledAtDesc(Long planId);

    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO elder_medication_record
                        (org_id, workspace_id, elder_id, plan_id, scheduled_at,
                         result, source, recorded_at, source_reference, owner_id,
                         create_time, update_time, deleted)
                    VALUES
                        (:orgId, :workspaceId, :elderId, :planId, :scheduledAt,
                         :result, :source, :recordedAt, :sourceReference, :ownerId,
                         CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
                    ON CONFLICT (plan_id, scheduled_at) WHERE deleted = FALSE DO NOTHING
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("orgId") Long orgId,
            @Param("workspaceId") Long workspaceId,
            @Param("elderId") Long elderId,
            @Param("planId") Long planId,
            @Param("scheduledAt") LocalDateTime scheduledAt,
            @Param("result") String result,
            @Param("source") String source,
            @Param("recordedAt") LocalDateTime recordedAt,
            @Param("sourceReference") String sourceReference,
            @Param("ownerId") Long ownerId);
}
