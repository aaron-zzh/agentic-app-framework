package com.xuejiai.aaf.module.knowledge.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeBase;

/** 知识库仓储。 */
public interface KnowledgeBaseRepository extends CrudEntityRepository<KnowledgeBase> {

    Optional<KnowledgeBase> findByStableId(UUID stableId);

    @Query(
            """
            SELECT b FROM KnowledgeBase b
            WHERE b.deleted = false AND b.status = 0
              AND (b.stableId IN :requestedIds OR b.visibility = 'SYSTEM_PUBLIC')
            ORDER BY b.stableId
            """)
    List<KnowledgeBase> findSearchCandidates(@Param("requestedIds") Collection<UUID> requestedIds);
}
