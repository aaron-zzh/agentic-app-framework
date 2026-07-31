package com.xuejiai.aaf.module.knowledge.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.pipeline.KnowledgeDocumentStatusPort;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;

import lombok.RequiredArgsConstructor;

/** 知识文档状态持久化适配器。 */
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentStatusAdapter implements KnowledgeDocumentStatusPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void updateStatus(Long documentId, int status, int chunkCount, String errorMessage) {
        var updated =
                jdbcTemplate.update(
                        """
                        UPDATE ai_knowledge_document
                        SET status = ?, chunk_count = ?, error_message = ?, update_time = CURRENT_TIMESTAMP
                        WHERE id = ? AND deleted = false
                        """,
                        status,
                        chunkCount,
                        errorMessage,
                        documentId);
        if (updated != 1) {
            throw new TaskExecutionInProgressException("知识文档不存在或已删除，停止状态更新: " + documentId);
        }
    }
}
