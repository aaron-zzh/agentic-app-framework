package com.xuejiai.aaf.module.ai.aigc.project.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectExecutionReservation;

import jakarta.persistence.LockModeType;

public interface AigcProjectExecutionReservationRepository
        extends JpaRepository<AigcProjectExecutionReservation, Long> {

    Optional<AigcProjectExecutionReservation> findByProjectIdAndExecutionSubmissionId(
            Long projectId, Long executionSubmissionId);

    Optional<AigcProjectExecutionReservation> findByProjectIdAndIdempotencyKey(
            Long projectId, String idempotencyKey);

    boolean existsByProjectIdAndStatusIn(Long projectId, Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select reservation from AigcProjectExecutionReservation reservation where reservation.id = :id")
    Optional<AigcProjectExecutionReservation> findLockedById(@Param("id") Long id);
}
