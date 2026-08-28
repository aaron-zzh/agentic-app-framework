package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromptEnvelopeRepository extends JpaRepository<PromptEnvelopeEntity, Long> {

    Optional<PromptEnvelopeEntity> findFirstByTenantIdAndExecutionIdOrderByEnvelopeSeqDesc(
            String tenantId, String executionId);

    List<PromptEnvelopeEntity> findByTenantIdAndExecutionIdOrderByEnvelopeSeqAsc(
            String tenantId, String executionId);

    /** 同一事务内取下一个序号；唯一约束是并发兜底。 */
    @Query(
            """
            SELECT COALESCE(MAX(e.envelopeSeq), 0)
            FROM PromptEnvelopeEntity e
            WHERE e.tenantId = :tenantId AND e.executionId = :executionId
            """)
    int findMaxSeq(@Param("tenantId") String tenantId, @Param("executionId") String executionId);
}
