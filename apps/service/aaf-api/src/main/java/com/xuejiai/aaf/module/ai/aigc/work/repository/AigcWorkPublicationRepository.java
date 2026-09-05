package com.xuejiai.aaf.module.ai.aigc.work.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.ai.aigc.work.domain.AigcWorkPublication;

import jakarta.persistence.LockModeType;

public interface AigcWorkPublicationRepository extends JpaRepository<AigcWorkPublication, Long> {

    List<AigcWorkPublication> findByWorkIdIn(List<Long> workIds);

    List<AigcWorkPublication> findByWorkIdOrderByIdDesc(Long workId);

    Optional<AigcWorkPublication> findByWorkIdAndIdempotencyKey(Long workId, String idempotencyKey);

    List<AigcWorkPublication> findByRetryOfPublicationIdOrderByRetryCountDesc(
            Long retryOfPublicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select publication from AigcWorkPublication publication where publication.id = :id")
    Optional<AigcWorkPublication> findLockedById(@Param("id") Long id);
}
