package com.xuejiai.aaf.framework.engine.knowledge.pipeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档增量更新服务 — 通过内容 hash 对比决定是否需要重新分块和生成 embedding。
 */
@Service
@RequiredArgsConstructor
public class IncrementalUpdateService {

    private final EntityManager entityManager;

    /** 对比新旧 hash，判断文档是否需要更新 */
    public boolean needsUpdate(Long documentId, String newContentHash) {
        var result = entityManager.createNativeQuery(
                        "SELECT content_hash FROM knowledge_document WHERE id = :id AND deleted = false")
                .setParameter("id", documentId)
                .getResultList();
        if (result.isEmpty()) return false;
        var oldHash = (String) result.getFirst();
        return !newContentHash.equals(oldHash);
    }

    /** 删除文档关联的旧 embedding 和 chunk */
    @Transactional
    public void cleanOldData(Long documentId) {
        entityManager.createNativeQuery(
                        "DELETE FROM knowledge_embedding WHERE chunk_id IN (SELECT id FROM knowledge_chunk WHERE document_id = :docId)")
                .setParameter("docId", documentId)
                .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM knowledge_chunk WHERE document_id = :docId")
                .setParameter("docId", documentId)
                .executeUpdate();
    }

    /** 更新文档的 content_hash 和 updated_at */
    @Transactional
    public void updateDocumentHash(Long documentId, String newHash) {
        entityManager.createNativeQuery(
                        "UPDATE knowledge_document SET content_hash = :hash, updated_at = :now WHERE id = :id")
                .setParameter("hash", newHash)
                .setParameter("now", LocalDateTime.now())
                .setParameter("id", documentId)
                .executeUpdate();
    }

    /** 计算内容的 SHA-256 哈希 */
    public String computeHash(String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
