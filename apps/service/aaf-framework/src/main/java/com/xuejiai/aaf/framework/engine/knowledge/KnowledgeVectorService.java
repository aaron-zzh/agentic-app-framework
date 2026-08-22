package com.xuejiai.aaf.framework.engine.knowledge;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingProperties;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.SourceFilters;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeUsagePort.BillingContext;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.ProjectionSnapshot;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.RunContext;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.StoredChunk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 知识库 PgVector 可重建投影。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeVectorService {

    private static final String EMBEDDING_VERSION = "v1";

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final EmbeddingProperties properties;
    private final TrustedKnowledgeStore truthStore;

    public void store(RunContext run, List<StoredChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        var vectors =
                embeddingService.embedKnowledgeBatch(
                        chunks.stream().map(StoredChunk::content).toList(),
                        run.billing(),
                        "chunks");
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("知识块与向量数量不一致");
        }
        var source =
                jdbcTemplate.queryForObject(
                        """
                        SELECT b.stable_id, d.stable_id, d.source_type, d.source_key
                        FROM ai_knowledge_base b
                        JOIN ai_knowledge_document d ON d.knowledge_base_id = b.id
                        WHERE b.id = ? AND d.id = ? AND b.deleted = false AND d.deleted = false
                        """,
                        (rs, rowNum) ->
                                new VectorSource(
                                        rs.getObject(1, UUID.class),
                                        rs.getObject(2, UUID.class),
                                        rs.getString(3),
                                        rs.getString(4)),
                        run.knowledgeBaseId(),
                        run.documentId());
        var baseStableId = Objects.requireNonNull(source).knowledgeBaseId();
        for (var index = 0; index < chunks.size(); index++) {
            var chunk = chunks.get(index);
            var metadata = new LinkedHashMap<String, Object>();
            metadata.put("chunk_id", chunk.stableId().toString());
            metadata.put("run_id", run.runId().toString());
            metadata.put("knowledge_base_id", baseStableId.toString());
            metadata.put("document_id", source.documentId().toString());
            metadata.put("source_type", source.sourceType());
            if (source.sourceKey() != null) {
                metadata.put("source_key", source.sourceKey());
            }
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_knowledge_embedding
                        (chunk_id, run_id, document_id, knowledge_base_id, knowledge_base_pk,
                         model_id, embedding_version, content_hash, content, metadata, embedding)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS vector))
                    ON CONFLICT (chunk_id, model_id, embedding_version) DO UPDATE SET
                        content_hash = EXCLUDED.content_hash,
                        content = EXCLUDED.content,
                        metadata = EXCLUDED.metadata,
                        embedding = EXCLUDED.embedding
                    """,
                    chunk.stableId(),
                    run.runId(),
                    run.documentId(),
                    baseStableId,
                    run.knowledgeBaseId(),
                    properties.model(),
                    EMBEDDING_VERSION,
                    chunk.contentHash(),
                    chunk.content(),
                    JsonUtils.toJsonString(metadata),
                    vector(vectors.get(index)));
        }
        log.info("写入 {} 条知识向量，runId={}", chunks.size(), run.runId());
    }

    public List<org.springframework.ai.document.Document> search(
            String query, int topK, String filterExpression) {
        if (filterExpression == null || filterExpression.isBlank()) {
            throw new IllegalArgumentException("向量检索必须提供授权知识库过滤表达式");
        }
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression(filterExpression)
                        .build());
    }

    /** 按知识库一致水位重建，并在最终锁定水位后验证两侧 chunk UUID 集合全等。 */
    public void rebuild(UUID knowledgeBaseId) {
        var snapshot = truthStore.projectionSnapshot(knowledgeBaseId);
        markVectorRebuilding(snapshot);
        try {
            var rows = currentChunks(snapshot.stableId());
            var grouped = new LinkedHashMap<RunContext, List<StoredChunk>>();
            for (var row : rows) {
                grouped.computeIfAbsent(row.run(), ignored -> new java.util.ArrayList<>())
                        .add(row.chunk());
            }
            grouped.forEach(this::store);
            deleteStaleEmbeddings(snapshot.stableId());
            if (!markVectorReadyIfExact(snapshot)) {
                markVectorDegraded(snapshot, "重建期间知识库水位变化或 chunk UUID 集合不一致");
            }
        } catch (RuntimeException failure) {
            markVectorDegraded(snapshot, failure.getMessage());
            throw failure;
        }
    }

    private List<RebuildChunk> currentChunks(UUID knowledgeBaseId) {
        return jdbcTemplate.query(
                """
                SELECT r.id, r.run_no, r.knowledge_base_id, r.document_id,
                       r.payer_user_id, COALESCE(CAST(d.org_id AS text), 'system'),
                       r.expected_active_run_id, r.fencing_token,
                       r.extraction_prompt_snapshot, r.extraction_prompt_digest,
                       r.extraction_prompt_version, r.extraction_user_prompt_snapshot,
                       r.extraction_user_prompt_digest, r.extraction_user_prompt_version,
                       r.extraction_output_contract_version, r.extraction_model_id,
                       r.entity_resolution_prompt_snapshot, r.entity_resolution_prompt_digest,
                       r.entity_resolution_prompt_version,
                       r.entity_resolution_user_prompt_snapshot,
                       r.entity_resolution_user_prompt_digest,
                       r.entity_resolution_user_prompt_version,
                       r.entity_resolution_output_contract_version,
                       r.entity_resolution_model_id,
                       c.stable_id, c.chunk_index, c.content, c.content_hash,
                       c.token_count
                FROM ai_knowledge_base b
                JOIN ai_knowledge_document d ON d.knowledge_base_id = b.id
                    AND d.active_run_id IS NOT NULL AND d.deleted = false
                JOIN ai_knowledge_ingest_run r ON r.id = d.active_run_id
                JOIN ai_knowledge_chunk c ON c.run_id = r.id
                WHERE b.stable_id = ? AND b.deleted = false
                ORDER BY r.id, c.chunk_index
                """,
                (rs, rowNum) ->
                        new RebuildChunk(
                                new RunContext(
                                        rs.getObject(1, UUID.class),
                                        rs.getLong(2),
                                        rs.getLong(3),
                                        rs.getLong(4),
                                        new BillingContext(
                                                rs.getString(6),
                                                rs.getObject(1, UUID.class),
                                                rs.getLong(5)),
                                        rs.getObject(7, UUID.class),
                                        rs.getLong(8),
                                        "PUBLISHED",
                                        rs.getString(9),
                                        rs.getString(10),
                                        rs.getInt(11),
                                        rs.getString(12),
                                        rs.getString(13),
                                        rs.getInt(14),
                                        rs.getString(15),
                                        rs.getString(16),
                                        rs.getString(17),
                                        rs.getString(18),
                                        rs.getInt(19),
                                        rs.getString(20),
                                        rs.getString(21),
                                        rs.getInt(22),
                                        rs.getString(23),
                                        rs.getString(24)),
                                new StoredChunk(
                                        rs.getObject(25, UUID.class),
                                        rs.getInt(26),
                                        rs.getString(27),
                                        rs.getString(28),
                                        rs.getInt(29),
                                        Map.of())),
                knowledgeBaseId);
    }

    private void markVectorRebuilding(ProjectionSnapshot snapshot) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_knowledge_projection_checkpoint
                    (knowledge_base_id, projection_kind, desired_watermark,
                     applied_watermark, status)
                VALUES (?, 'PGVECTOR', ?, 0, 'REBUILDING')
                ON CONFLICT (knowledge_base_id, projection_kind) DO UPDATE SET
                    desired_watermark = GREATEST(
                        ai_knowledge_projection_checkpoint.desired_watermark,
                        EXCLUDED.desired_watermark),
                    status = CASE
                        WHEN ai_knowledge_projection_checkpoint.desired_watermark <= EXCLUDED.desired_watermark
                        THEN 'REBUILDING' ELSE ai_knowledge_projection_checkpoint.status END,
                    error_message = NULL, updated_at = CURRENT_TIMESTAMP
                """,
                snapshot.knowledgeBaseId(),
                snapshot.watermark());
    }

    private void deleteStaleEmbeddings(UUID knowledgeBaseId) {
        jdbcTemplate.update(
                """
                DELETE FROM ai_knowledge_embedding embedding
                USING ai_knowledge_ingest_run run
                WHERE embedding.run_id = run.id
                  AND embedding.knowledge_base_id = ?
                  AND run.status IN ('SUPERSEDED', 'FAILED')
                """,
                knowledgeBaseId);
    }

    private boolean markVectorReadyIfExact(ProjectionSnapshot snapshot) {
        return jdbcTemplate.update(
                        """
                        UPDATE ai_knowledge_projection_checkpoint checkpoint
                        SET applied_watermark = ?, status = 'READY', error_message = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        FROM ai_knowledge_base base
                        WHERE checkpoint.knowledge_base_id = base.id
                          AND checkpoint.knowledge_base_id = ?
                          AND checkpoint.projection_kind = 'PGVECTOR'
                          AND checkpoint.desired_watermark = ?
                          AND base.projection_watermark = ?
                          AND NOT EXISTS (
                              (SELECT chunk.stable_id
                               FROM ai_knowledge_chunk chunk
                               JOIN ai_knowledge_document document
                                 ON document.id = chunk.document_id
                                AND document.active_run_id = chunk.run_id
                                AND document.deleted = false
                               WHERE chunk.knowledge_base_id = base.id)
                              EXCEPT
                              (SELECT DISTINCT embedding.chunk_id
                               FROM ai_knowledge_embedding embedding
                               JOIN ai_knowledge_document document
                                 ON document.id = embedding.document_id
                                AND document.active_run_id = embedding.run_id
                                AND document.deleted = false
                               WHERE embedding.knowledge_base_id = base.stable_id))
                          AND NOT EXISTS (
                              (SELECT DISTINCT embedding.chunk_id
                               FROM ai_knowledge_embedding embedding
                               JOIN ai_knowledge_document document
                                 ON document.id = embedding.document_id
                                AND document.active_run_id = embedding.run_id
                                AND document.deleted = false
                               WHERE embedding.knowledge_base_id = base.stable_id)
                              EXCEPT
                              (SELECT chunk.stable_id
                               FROM ai_knowledge_chunk chunk
                               JOIN ai_knowledge_document document
                                 ON document.id = chunk.document_id
                                AND document.active_run_id = chunk.run_id
                                AND document.deleted = false
                               WHERE chunk.knowledge_base_id = base.id))
                        """,
                        snapshot.watermark(),
                        snapshot.knowledgeBaseId(),
                        snapshot.watermark(),
                        snapshot.watermark())
                == 1;
    }

    private void markVectorDegraded(ProjectionSnapshot snapshot, String error) {
        jdbcTemplate.update(
                """
                UPDATE ai_knowledge_projection_checkpoint
                SET status = 'DEGRADED', error_message = ?, updated_at = CURRENT_TIMESTAMP
                WHERE knowledge_base_id = ? AND projection_kind = 'PGVECTOR'
                  AND desired_watermark = ?
                """,
                error == null || error.length() <= 2000 ? error : error.substring(0, 2000),
                snapshot.knowledgeBaseId(),
                snapshot.watermark());
    }

    public Set<UUID> retainCurrentChunkIds(
            Collection<UUID> chunkIds, UUID knowledgeBaseId, SourceFilters filters) {
        if (chunkIds.isEmpty()) {
            return Set.of();
        }
        var resolved = filters == null ? new SourceFilters(Set.of(), Set.of(), Set.of()) : filters;
        return Set.copyOf(
                jdbcTemplate.query(
                        """
                        SELECT c.stable_id
                        FROM ai_knowledge_chunk c
                        JOIN ai_knowledge_document d ON d.id = c.document_id
                            AND d.active_run_id = c.run_id AND d.deleted = false
                        JOIN ai_knowledge_base b ON b.id = c.knowledge_base_id AND b.deleted = false
                        WHERE c.stable_id = ANY(CAST(? AS uuid[]))
                          AND b.stable_id = ?
                          AND (CAST(? AS text[]) = '{}'::text[] OR d.source_type = ANY(CAST(? AS text[])))
                          AND (CAST(? AS text[]) = '{}'::text[] OR d.source_key = ANY(CAST(? AS text[])))
                          AND (CAST(? AS uuid[]) = '{}'::uuid[] OR d.stable_id = ANY(CAST(? AS uuid[])))
                        """,
                        (rs, rowNum) -> rs.getObject(1, UUID.class),
                        uuidArray(chunkIds),
                        knowledgeBaseId,
                        textArray(resolved.sourceTypes()),
                        textArray(resolved.sourceTypes()),
                        textArray(resolved.sourceKeys()),
                        textArray(resolved.sourceKeys()),
                        uuidArray(resolved.documentIds()),
                        uuidArray(resolved.documentIds())));
    }

    private String textArray(Iterable<String> values) {
        var items = new LinkedHashSet<String>();
        values.forEach(value -> items.add(value.replace("\\", "\\\\").replace("\"", "\\\"")));
        if (items.isEmpty()) {
            return "{}";
        }
        return "{\"" + String.join("\",\"", items) + "\"}";
    }

    private String uuidArray(Iterable<UUID> values) {
        var items = new LinkedHashSet<String>();
        values.forEach(value -> items.add(value.toString()));
        return "{" + String.join(",", items) + "}";
    }

    private String vector(float[] values) {
        var builder = new StringBuilder("[");
        for (var index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(values[index]);
        }
        return builder.append(']').toString();
    }

    private record RebuildChunk(RunContext run, StoredChunk chunk) {}

    private record VectorSource(
            UUID knowledgeBaseId, UUID documentId, String sourceType, String sourceKey) {}
}
