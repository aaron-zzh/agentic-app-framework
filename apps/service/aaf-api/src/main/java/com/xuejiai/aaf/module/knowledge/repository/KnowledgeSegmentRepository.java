package com.xuejiai.aaf.module.knowledge.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.knowledge.domain.KnowledgeSegment;

/**
 * 知识库段落仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface KnowledgeSegmentRepository extends JpaRepository<KnowledgeSegment, Long> {

    Page<KnowledgeSegment> findByDocumentId(Long documentId, Pageable pageable);

    java.util.List<KnowledgeSegment> findByKnowledgeBaseId(Long knowledgeBaseId);
}
