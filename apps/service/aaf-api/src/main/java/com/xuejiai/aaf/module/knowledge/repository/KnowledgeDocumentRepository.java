package com.xuejiai.aaf.module.knowledge.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;

/** 知识库文档仓储。 */
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    Page<KnowledgeDocument> findByKnowledgeBaseId(Long knowledgeBaseId, Pageable pageable);

    Optional<KnowledgeDocument> findByIdAndKnowledgeBaseId(Long id, Long knowledgeBaseId);

    @Query(
            "SELECT COALESCE(SUM(d.fileSize), 0) FROM KnowledgeDocument d WHERE d.knowledgeBaseId = :knowledgeBaseId AND d.deleted = false")
    long sumFileSizeByKnowledgeBaseId(Long knowledgeBaseId);

    @Query(
            "SELECT COUNT(d) FROM KnowledgeDocument d WHERE d.knowledgeBaseId = :knowledgeBaseId AND d.deleted = false")
    long countByKnowledgeBaseId(Long knowledgeBaseId);

    @Query(
            "SELECT COUNT(d) FROM KnowledgeDocument d WHERE d.knowledgeBaseId = :knowledgeBaseId AND d.status = :status AND d.deleted = false")
    long countByKnowledgeBaseIdAndStatus(Long knowledgeBaseId, Integer status);

    @Query(
            "SELECT COALESCE(SUM(d.chunkCount), 0) FROM KnowledgeDocument d WHERE d.knowledgeBaseId = :knowledgeBaseId AND d.deleted = false")
    long sumChunkCountByKnowledgeBaseId(Long knowledgeBaseId);

    @Query(
            """
            SELECT d FROM KnowledgeDocument d
            WHERE d.id > :afterId
              AND d.id <= :cycleMaxId
              AND d.deleted = false
            ORDER BY d.id ASC
            """)
    List<KnowledgeDocument> findRecoveryScanWindow(
            @Param("afterId") Long afterId,
            @Param("cycleMaxId") Long cycleMaxId,
            Pageable pageable);

    @Query("SELECT COALESCE(MAX(d.id), 0) FROM KnowledgeDocument d WHERE d.deleted = false")
    long findRecoveryCycleMaxId();

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
            """
            UPDATE KnowledgeDocument d
            SET d.updateTime = CURRENT_TIMESTAMP
            WHERE d.id = :documentId
              AND d.status = :status
              AND d.updateTime = :observedUpdateTime
              AND d.deleted = false
            """)
    int touchDispatchTimeIfPending(
            @Param("documentId") Long documentId,
            @Param("status") Integer status,
            @Param("observedUpdateTime") LocalDateTime observedUpdateTime);
}
