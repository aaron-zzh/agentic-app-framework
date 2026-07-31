package com.xuejiai.aaf.framework.engine.knowledge.pipeline;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.DocumentChunk;

import lombok.RequiredArgsConstructor;

/** 知识块关系库存储，负责按文档原子替换块数据。 */
@Component
@RequiredArgsConstructor
public class KnowledgeChunkStore {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public List<Long> replace(Long knowledgeBaseId, Long documentId, List<DocumentChunk> chunks) {
        clearData(documentId);

        var chunkIds = new ArrayList<Long>(chunks.size());
        for (var chunk : chunks) {
            var id =
                    jdbcTemplate.queryForObject(
                            """
                            INSERT INTO ai_knowledge_chunk
                                (document_id, knowledge_base_id, content, chunk_index, metadata, token_count)
                            VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)
                            RETURNING id
                            """,
                            Long.class,
                            documentId,
                            knowledgeBaseId,
                            chunk.content(),
                            chunk.index(),
                            JsonUtils.toJsonString(chunk.metadata()),
                            chunk.tokenCount());
            if (id == null) {
                throw new IllegalStateException("知识块写入未返回 ID");
            }
            chunkIds.add(id);
        }
        return List.copyOf(chunkIds);
    }

    @Transactional
    public void clear(Long documentId) {
        clearData(documentId);
    }

    private void clearData(Long documentId) {
        jdbcTemplate.update(
                "DELETE FROM ai_knowledge_embedding WHERE metadata ->> 'document_id' = ?",
                String.valueOf(documentId));
        jdbcTemplate.update("DELETE FROM ai_knowledge_chunk WHERE document_id = ?", documentId);
    }
}
