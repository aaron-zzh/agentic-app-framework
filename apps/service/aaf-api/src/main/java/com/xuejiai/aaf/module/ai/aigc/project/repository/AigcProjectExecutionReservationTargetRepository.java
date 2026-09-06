package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectExecutionReservationTarget;

public interface AigcProjectExecutionReservationTargetRepository
        extends JpaRepository<AigcProjectExecutionReservationTarget, Long> {

    List<AigcProjectExecutionReservationTarget> findByReservationIdOrderByIdAsc(Long reservationId);

    boolean existsByReservationIdAndProjectObjectId(Long reservationId, Long projectObjectId);

    List<AigcProjectExecutionReservationTarget> findByProjectObjectIdInOrderByIdAsc(
            Collection<Long> projectObjectIds);

    boolean existsByProjectObjectId(Long projectObjectId);

    boolean existsByProjectObjectIdIn(Collection<Long> projectObjectIds);

    @Query(
            value =
                    "select exists(select 1 from aigc_project_execution_reservation_target target join aigc_project_execution_reservation reservation on reservation.id = target.reservation_id where target.project_object_id = :objectId and reservation.deleted = false and reservation.status in ('PREPARED', 'BOUND'))",
            nativeQuery = true)
    boolean existsActiveReservationByProjectObjectId(@Param("objectId") Long objectId);
}
