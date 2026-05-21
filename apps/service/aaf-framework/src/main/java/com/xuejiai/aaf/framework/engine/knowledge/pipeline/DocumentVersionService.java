package com.xuejiai.aaf.framework.engine.knowledge.pipeline;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** 文档版本管理服务 — 版本递增与旧向量清理。 */
@Service
@RequiredArgsConstructor
public class DocumentVersionService {

    private final EntityManager entityManager;

    /** 文档版本号 +1 */
    @Transactional
    public void incrementVersion(Long documentId) {
        entityManager
                .createNativeQuery(
                        "UPDATE knowledge_document SET version = COALESCE(version, 0) + 1 WHERE id = :docId")
                .setParameter("docId", documentId)
                .executeUpdate();
    }

    /** 删除该文档的所有旧向量 */
    @Transactional
    public void cleanOldEmbeddings(Long documentId) {
        entityManager
                .createNativeQuery(
                        "DELETE FROM knowledge_embedding WHERE chunk_id IN (SELECT id FROM knowledge_chunk WHERE document_id = :docId)")
                .setParameter("docId", documentId)
                .executeUpdate();
    }
}
