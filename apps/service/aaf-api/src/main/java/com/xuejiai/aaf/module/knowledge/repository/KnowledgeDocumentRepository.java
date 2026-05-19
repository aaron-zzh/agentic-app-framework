package com.xuejiai.aaf.module.knowledge.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;

/** 知识库文档仓储。 */
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    Page<KnowledgeDocument> findByKnowledgeBaseId(Long knowledgeBaseId, Pageable pageable);

    @Query("SELECT COUNT(d) FROM KnowledgeDocument d WHERE d.knowledgeBaseId = :knowledgeBaseId AND d.deleted = false")
    long countByKnowledgeBaseId(Long knowledgeBaseId);
}
