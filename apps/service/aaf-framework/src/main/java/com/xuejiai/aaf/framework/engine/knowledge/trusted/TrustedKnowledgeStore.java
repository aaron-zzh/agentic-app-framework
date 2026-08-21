package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.DocumentChunk;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.SourceFilters;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.SourceRef;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.Visibility;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeUsagePort.BillingContext;

import lombok.RequiredArgsConstructor;

/** NexusKB PostgreSQL 真理存储。所有投影只从本组件读取规范事实。 */
@Component
@RequiredArgsConstructor
public class TrustedKnowledgeStore {

    private static final String NORMALIZATION_VERSION = "v1";

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public RunContext beginRun(
            Long knowledgeBaseId,
            Long documentId,
            String contentHash,
            String fingerprint,
            String parserVersion,
            String chunkDigest,
            KnowledgeIngestConfigurationService.Snapshot configuration,
            String embeddingModelId) {
        var source =
                jdbcTemplate.queryForObject(
                        """
                        SELECT b.owner_id, d.uploaded_by, COALESCE(CAST(d.org_id AS text), 'system'),
                               d.active_run_id, d.ingest_fence
                        FROM ai_knowledge_document d
                        JOIN ai_knowledge_base b ON b.id = d.knowledge_base_id AND b.deleted = false
                        WHERE d.id = ? AND d.knowledge_base_id = ? AND d.deleted = false
                        FOR UPDATE OF d
                        """,
                        (rs, rowNum) ->
                                new RunSource(
                                        rs.getObject(1, Long.class),
                                        rs.getObject(2, Long.class),
                                        rs.getString(3),
                                        rs.getObject(4, UUID.class),
                                        rs.getLong(5)),
                        documentId,
                        knowledgeBaseId);
        if (source == null) {
            throw new IllegalStateException("知识来源不存在: " + documentId);
        }

        var existing = findRun(documentId, fingerprint);
        if (existing.isPresent()) {
            var run = existing.get();
            if ("FAILED".equals(run.status())) {
                var fencingToken = source.ingestFence() + 1;
                jdbcTemplate.update(
                        "UPDATE ai_knowledge_document SET ingest_fence = ? WHERE id = ?",
                        fencingToken,
                        documentId);
                jdbcTemplate.update(
                        """
                        UPDATE ai_knowledge_ingest_run
                        SET status = 'PROCESSING', error_message = NULL, finished_at = NULL,
                            expected_active_run_id = ?, fencing_token = ?
                        WHERE id = ?
                        """,
                        source.activeRunId(),
                        fencingToken,
                        run.runId());
                return run.context(source.activeRunId(), fencingToken, "PROCESSING");
            }
            return run.context();
        }

        var payer = source.uploadedBy() != null ? source.uploadedBy() : source.ownerId();
        if (payer == null || payer <= 0) {
            throw new IllegalStateException("知识来源缺少上传者且知识库没有拥有者");
        }
        var runId = UUID.randomUUID();
        var runNo =
                Objects.requireNonNullElse(
                        jdbcTemplate.queryForObject(
                                "SELECT COALESCE(MAX(run_no), 0) + 1 FROM ai_knowledge_ingest_run WHERE document_id = ?",
                                Long.class,
                                documentId),
                        1L);
        var fencingToken = source.ingestFence() + 1;
        jdbcTemplate.update(
                "UPDATE ai_knowledge_document SET ingest_fence = ? WHERE id = ?",
                fencingToken,
                documentId);
        jdbcTemplate.update(
                """
                INSERT INTO ai_knowledge_ingest_run
                    (id, knowledge_base_id, document_id, run_no, ingest_fingerprint,
                     payer_user_id, status, parser_version, chunk_config_digest,
                     extraction_prompt_snapshot, extraction_prompt_digest, extraction_prompt_version,
                     extraction_user_prompt_snapshot, extraction_user_prompt_digest, extraction_user_prompt_version,
                     extraction_output_contract_version, extraction_model_id,
                     entity_resolution_prompt_snapshot, entity_resolution_prompt_digest, entity_resolution_prompt_version,
                     entity_resolution_user_prompt_snapshot, entity_resolution_user_prompt_digest, entity_resolution_user_prompt_version,
                     entity_resolution_output_contract_version, entity_resolution_model_id,
                     normalization_version, embedding_model_id,
                     expected_active_run_id, fencing_token)
                VALUES (?, ?, ?, ?, ?, ?, 'PROCESSING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                runId,
                knowledgeBaseId,
                documentId,
                runNo,
                fingerprint,
                payer,
                parserVersion,
                chunkDigest,
                configuration.extractionSystem().content(),
                configuration.extractionSystem().sha256(),
                configuration.extractionSystem().version(),
                configuration.extractionUser().content(),
                configuration.extractionUser().sha256(),
                configuration.extractionUser().version(),
                configuration.extractionOutputContractVersion(),
                configuration.extractionModelId(),
                configuration.entityResolutionSystem().content(),
                configuration.entityResolutionSystem().sha256(),
                configuration.entityResolutionSystem().version(),
                configuration.entityResolutionUser().content(),
                configuration.entityResolutionUser().sha256(),
                configuration.entityResolutionUser().version(),
                configuration.entityResolutionOutputContractVersion(),
                configuration.entityResolutionModelId(),
                NORMALIZATION_VERSION,
                embeddingModelId,
                source.activeRunId(),
                fencingToken);
        jdbcTemplate.update(
                "UPDATE ai_knowledge_document SET content_hash = ?, status = 1, error_message = NULL WHERE id = ?",
                contentHash,
                documentId);
        return new RunContext(
                runId,
                runNo,
                knowledgeBaseId,
                documentId,
                new BillingContext(source.tenantId(), runId, payer),
                source.activeRunId(),
                fencingToken,
                "PROCESSING",
                configuration.extractionSystem().content(),
                configuration.extractionSystem().sha256(),
                configuration.extractionSystem().version(),
                configuration.extractionUser().content(),
                configuration.extractionUser().sha256(),
                configuration.extractionUser().version(),
                configuration.extractionOutputContractVersion(),
                configuration.extractionModelId(),
                configuration.entityResolutionSystem().content(),
                configuration.entityResolutionSystem().sha256(),
                configuration.entityResolutionSystem().version(),
                configuration.entityResolutionUser().content(),
                configuration.entityResolutionUser().sha256(),
                configuration.entityResolutionUser().version(),
                configuration.entityResolutionOutputContractVersion(),
                configuration.entityResolutionModelId());
    }

    private Optional<StoredRun> findRun(Long documentId, String fingerprint) {
        return jdbcTemplate
                .query(
                        """
                        SELECT r.id, r.run_no, r.knowledge_base_id, r.document_id, r.payer_user_id,
                               COALESCE(CAST(d.org_id AS text), 'system'), r.expected_active_run_id,
                               r.fencing_token, r.status, r.extraction_prompt_snapshot,
                               r.extraction_prompt_digest, r.extraction_prompt_version,
                               r.extraction_user_prompt_snapshot, r.extraction_user_prompt_digest,
                               r.extraction_user_prompt_version, r.extraction_output_contract_version,
                               r.extraction_model_id, r.entity_resolution_prompt_snapshot,
                               r.entity_resolution_prompt_digest, r.entity_resolution_prompt_version,
                               r.entity_resolution_user_prompt_snapshot,
                               r.entity_resolution_user_prompt_digest,
                               r.entity_resolution_user_prompt_version,
                               r.entity_resolution_output_contract_version,
                               r.entity_resolution_model_id
                        FROM ai_knowledge_ingest_run r
                        JOIN ai_knowledge_document d ON d.id = r.document_id
                        WHERE r.document_id = ? AND r.ingest_fingerprint = ?
                          AND r.status <> 'SUPERSEDED'
                        """,
                        (rs, rowNum) ->
                                new StoredRun(
                                        rs.getObject(1, UUID.class),
                                        rs.getLong(2),
                                        rs.getLong(3),
                                        rs.getLong(4),
                                        rs.getLong(5),
                                        rs.getString(6),
                                        rs.getObject(7, UUID.class),
                                        rs.getLong(8),
                                        rs.getString(9),
                                        rs.getString(10),
                                        rs.getString(11),
                                        rs.getInt(12),
                                        rs.getString(13),
                                        rs.getString(14),
                                        rs.getInt(15),
                                        rs.getString(16),
                                        rs.getString(17),
                                        rs.getString(18),
                                        rs.getString(19),
                                        rs.getInt(20),
                                        rs.getString(21),
                                        rs.getString(22),
                                        rs.getInt(23),
                                        rs.getString(24),
                                        rs.getString(25)),
                        documentId,
                        fingerprint)
                .stream()
                .findFirst();
    }

    @Transactional
    public List<StoredChunk> storeChunks(RunContext run, List<DocumentChunk> chunks) {
        var stored = new ArrayList<StoredChunk>(chunks.size());
        for (var chunk : chunks) {
            var contentHash = sha256(chunk.content());
            var metadata = chunk.metadata() == null ? Map.<String, Object>of() : chunk.metadata();
            var sectionPath = Objects.toString(metadata.get("section_path"), null);
            var pageNo = number(metadata.get("page"));
            var startOffset = number(metadata.get("start_offset"));
            var endOffset = number(metadata.get("end_offset"));
            var stableId =
                    jdbcTemplate.queryForObject(
                            """
                            INSERT INTO ai_knowledge_chunk
                                (run_id, document_id, knowledge_base_id, content, content_hash,
                                 chunk_index, token_count, section_path, page_no, start_offset,
                                 end_offset, metadata)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                            ON CONFLICT (run_id, chunk_index) DO UPDATE SET
                                content = EXCLUDED.content,
                                content_hash = EXCLUDED.content_hash,
                                token_count = EXCLUDED.token_count,
                                section_path = EXCLUDED.section_path,
                                page_no = EXCLUDED.page_no,
                                start_offset = EXCLUDED.start_offset,
                                end_offset = EXCLUDED.end_offset,
                                metadata = EXCLUDED.metadata
                            RETURNING stable_id
                            """,
                            UUID.class,
                            run.runId(),
                            run.documentId(),
                            run.knowledgeBaseId(),
                            chunk.content(),
                            contentHash,
                            chunk.index(),
                            chunk.tokenCount(),
                            sectionPath,
                            pageNo,
                            startOffset,
                            endOffset,
                            JsonUtils.toJsonString(metadata));
            stored.add(
                    new StoredChunk(
                            Objects.requireNonNull(stableId),
                            chunk.index(),
                            chunk.content(),
                            contentHash,
                            chunk.tokenCount(),
                            metadata));
        }
        return List.copyOf(stored);
    }

    public List<EntityCandidate> findExactEntities(Long knowledgeBaseId, String normalizedAlias) {
        return jdbcTemplate.query(
                """
                SELECT e.id, e.canonical_name, e.entity_type, e.description
                FROM ai_knowledge_entity_alias a
                JOIN ai_knowledge_entity e ON e.id = a.entity_id AND e.merged_into_id IS NULL
                WHERE a.knowledge_base_id = ? AND a.normalized_alias = ?
                ORDER BY a.confidence DESC, a.entity_id
                """,
                (rs, rowNum) ->
                        new EntityCandidate(
                                rs.getObject(1, UUID.class),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getString(4)),
                knowledgeBaseId,
                normalizedAlias);
    }

    public List<EntityCandidate> findEntityCandidates(
            Long knowledgeBaseId, String normalizedAlias, float[] mentionEmbedding, int limit) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT e.id, e.canonical_name, e.entity_type, e.description,
                       CASE WHEN vector.entity_id IS NULL THEN 2.0
                            ELSE vector.embedding <=> CAST(? AS vector) END AS distance
                FROM ai_knowledge_entity e
                LEFT JOIN ai_knowledge_entity_alias a ON a.entity_id = e.id
                LEFT JOIN ai_knowledge_entity_embedding vector ON vector.entity_id = e.id
                WHERE e.knowledge_base_id = ? AND e.merged_into_id IS NULL
                  AND (vector.entity_id IS NOT NULL
                       OR lower(e.canonical_name) LIKE ? OR a.normalized_alias LIKE ?)
                ORDER BY distance, e.canonical_name, e.id
                LIMIT ?
                """,
                (rs, rowNum) ->
                        new EntityCandidate(
                                rs.getObject(1, UUID.class),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getString(4)),
                vector(mentionEmbedding),
                knowledgeBaseId,
                "%" + normalizedAlias + "%",
                "%" + normalizedAlias + "%",
                limit);
    }

    @Transactional
    public UUID createOrAliasEntity(
            Long knowledgeBaseId,
            UUID runId,
            String name,
            String type,
            String description,
            String normalizedAlias,
            boolean review) {
        var key = sha256(NORMALIZATION_VERSION + "|" + normalize(type) + "|" + normalizedAlias);
        var entityId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO ai_knowledge_entity
                            (knowledge_base_id, canonical_name, entity_type, entity_key,
                             description, review_status)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (knowledge_base_id, entity_key) DO UPDATE SET
                            description = COALESCE(ai_knowledge_entity.description, EXCLUDED.description),
                            updated_at = CURRENT_TIMESTAMP
                        RETURNING id
                        """,
                        UUID.class,
                        knowledgeBaseId,
                        name,
                        type,
                        key,
                        description,
                        review ? "REVIEW" : "RESOLVED");
        jdbcTemplate.update(
                """
                INSERT INTO ai_knowledge_entity_alias
                    (knowledge_base_id, entity_id, normalized_alias, confidence, source_run_id)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (knowledge_base_id, normalized_alias, entity_id) DO NOTHING
                """,
                knowledgeBaseId,
                entityId,
                normalizedAlias,
                review ? 0.5 : 1.0,
                runId);
        return Objects.requireNonNull(entityId);
    }

    @Transactional
    public PersistedFact persistFact(
            RunContext run,
            UUID subjectId,
            String predicate,
            UUID objectId,
            double confidence,
            UUID focusChunkId,
            String quote,
            int startOffset,
            int endOffset,
            List<UUID> contextChunkIds,
            FactAssertion assertion) {
        var normalizedPredicate = normalize(predicate);
        var factKey =
                sha256(
                        NORMALIZATION_VERSION
                                + "|"
                                + subjectId
                                + "|"
                                + normalizedPredicate
                                + "|ENTITY|"
                                + objectId);
        var factId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO ai_knowledge_fact
                            (knowledge_base_id, subject_entity_id, predicate, object_kind,
                             object_entity_id, fact_key, confidence)
                        VALUES (?, ?, ?, 'ENTITY', ?, ?, ?)
                        ON CONFLICT (knowledge_base_id, fact_key) DO UPDATE SET
                            confidence = GREATEST(ai_knowledge_fact.confidence, EXCLUDED.confidence),
                            updated_at = CURRENT_TIMESTAMP
                        RETURNING id
                        """,
                        UUID.class,
                        run.knowledgeBaseId(),
                        subjectId,
                        predicate.trim(),
                        objectId,
                        factKey,
                        confidence);
        return persistEvidence(
                run,
                Objects.requireNonNull(factId),
                factKey,
                confidence,
                focusChunkId,
                quote,
                startOffset,
                endOffset,
                contextChunkIds,
                assertion);
    }

    @Transactional
    public PersistedFact persistLiteralFact(
            RunContext run,
            UUID subjectId,
            String predicate,
            String literal,
            double confidence,
            UUID focusChunkId,
            String quote,
            int startOffset,
            int endOffset,
            List<UUID> contextChunkIds,
            FactAssertion assertion) {
        var normalizedPredicate = normalize(predicate);
        var normalizedLiteral = normalize(literal);
        var factKey =
                sha256(
                        NORMALIZATION_VERSION
                                + "|"
                                + subjectId
                                + "|"
                                + normalizedPredicate
                                + "|LITERAL|"
                                + normalizedLiteral);
        var factId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO ai_knowledge_fact
                            (knowledge_base_id, subject_entity_id, predicate, object_kind,
                             object_literal, fact_key, confidence)
                        VALUES (?, ?, ?, 'LITERAL', ?, ?, ?)
                        ON CONFLICT (knowledge_base_id, fact_key) DO UPDATE SET
                            confidence = GREATEST(ai_knowledge_fact.confidence, EXCLUDED.confidence),
                            updated_at = CURRENT_TIMESTAMP
                        RETURNING id
                        """,
                        UUID.class,
                        run.knowledgeBaseId(),
                        subjectId,
                        predicate.trim(),
                        literal.trim(),
                        factKey,
                        confidence);
        return persistEvidence(
                run,
                Objects.requireNonNull(factId),
                factKey,
                confidence,
                focusChunkId,
                quote,
                startOffset,
                endOffset,
                contextChunkIds,
                assertion);
    }

    private PersistedFact persistEvidence(
            RunContext run,
            UUID factId,
            String factKey,
            double confidence,
            UUID focusChunkId,
            String quote,
            int startOffset,
            int endOffset,
            List<UUID> contextChunkIds,
            FactAssertion assertion) {
        Objects.requireNonNull(assertion, "assertion 不能为空");
        var quoteHash = sha256(quote + "|" + startOffset + "|" + endOffset);
        var evidenceId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO ai_knowledge_evidence
                            (run_id, document_id, knowledge_base_id, focus_chunk_id,
                             quote, quote_hash, context_chunk_ids)
                        VALUES (?, ?, ?, ?, ?, ?, CAST(? AS uuid[]))
                        ON CONFLICT (run_id, focus_chunk_id, quote_hash) DO UPDATE SET quote = EXCLUDED.quote
                        RETURNING id
                        """,
                        UUID.class,
                        run.runId(),
                        run.documentId(),
                        run.knowledgeBaseId(),
                        focusChunkId,
                        quote,
                        quoteHash,
                        uuidArray(contextChunkIds));
        jdbcTemplate.update(
                """
                INSERT INTO ai_knowledge_evidence_span (evidence_id, start_offset, end_offset)
                VALUES (?, ?, ?)
                ON CONFLICT (evidence_id, start_offset, end_offset) DO NOTHING
                """,
                evidenceId,
                startOffset,
                endOffset);
        jdbcTemplate.update(
                """
                INSERT INTO ai_knowledge_fact_evidence
                    (fact_id, evidence_id, knowledge_base_id, extract_confidence,
                     valid_at, invalid_at, attributes)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (fact_id, evidence_id) DO UPDATE SET
                    extract_confidence = GREATEST(
                        ai_knowledge_fact_evidence.extract_confidence,
                        EXCLUDED.extract_confidence),
                    valid_at = EXCLUDED.valid_at,
                    invalid_at = EXCLUDED.invalid_at,
                    attributes = EXCLUDED.attributes
                """,
                factId,
                evidenceId,
                run.knowledgeBaseId(),
                confidence,
                assertion.validAt(),
                assertion.invalidAt(),
                JsonUtils.toJsonString(assertion.attributes()));
        return new PersistedFact(
                Objects.requireNonNull(factId), Objects.requireNonNull(evidenceId), factKey);
    }

    @Transactional
    public void markReady(RunContext run) {
        var current = lockDocument(run.documentId());
        requirePublishFence(run, current);
        var updated =
                jdbcTemplate.update(
                        """
                        UPDATE ai_knowledge_ingest_run
                        SET status = 'READY', ready_at = transaction_timestamp()
                        WHERE id = ? AND document_id = ? AND run_no = ?
                          AND fencing_token = ?
                          AND expected_active_run_id IS NOT DISTINCT FROM ?
                          AND status = 'PROCESSING'
                        """,
                        run.runId(),
                        run.documentId(),
                        run.runNo(),
                        run.fencingToken(),
                        run.expectedActiveRunId());
        if (updated != 1 && !"READY".equals(runStatus(run))) {
            throw new IllegalStateException("知识代际 READY generation 校验失败");
        }
    }

    @Transactional
    public PublishReceipt publish(RunContext run) {
        var current = lockDocument(run.documentId());
        var status = runStatus(run);
        if ("PUBLISHED".equals(status)) {
            if (!run.runId().equals(current.activeRunId())) {
                throw new IllegalStateException("已发布知识代际不是文档当前代际");
            }
            var completed =
                    jdbcTemplate.update(
                            """
                            UPDATE ai_knowledge_document
                            SET status = 2,
                                chunk_count = (SELECT COUNT(*) FROM ai_knowledge_chunk WHERE run_id = ?),
                                error_message = NULL, update_time = transaction_timestamp()
                            WHERE id = ? AND active_run_id = ?
                            """,
                            run.runId(),
                            run.documentId(),
                            run.runId());
            if (completed != 1) {
                throw new IllegalStateException("已发布知识代际文档状态恢复失败");
            }
            return publicationReceipt(run);
        }
        requirePublishFence(run, current);
        if (!"READY".equals(status)) {
            throw new IllegalStateException("知识代际发布仅接受 READY 状态，当前状态: " + status);
        }

        jdbcTemplate.update(
                """
                UPDATE ai_knowledge_fact_evidence assertion
                SET recorded_at = transaction_timestamp()
                FROM ai_knowledge_evidence evidence
                WHERE assertion.evidence_id = evidence.id
                  AND evidence.run_id = ?
                  AND assertion.recorded_at IS NULL
                """,
                run.runId());
        var previousRunId = current.activeRunId();
        if (previousRunId != null && !previousRunId.equals(run.runId())) {
            jdbcTemplate.update(
                    """
                    UPDATE ai_knowledge_fact_evidence assertion
                    SET expired_at = transaction_timestamp(),
                        expiration_reason = 'SOURCE_SUPERSEDED'
                    FROM ai_knowledge_evidence evidence
                    WHERE assertion.evidence_id = evidence.id
                      AND evidence.run_id = ?
                      AND assertion.recorded_at IS NOT NULL
                      AND assertion.expired_at IS NULL
                    """,
                    previousRunId);
            jdbcTemplate.update(
                    """
                    UPDATE ai_knowledge_ingest_run
                    SET status = 'SUPERSEDED', finished_at = transaction_timestamp()
                    WHERE id = ? AND status = 'PUBLISHED'
                    """,
                    previousRunId);
        }
        var switched =
                jdbcTemplate.update(
                        """
                        UPDATE ai_knowledge_document
                        SET active_run_id = ?, status = 2,
                            chunk_count = (SELECT COUNT(*) FROM ai_knowledge_chunk WHERE run_id = ?),
                            error_message = NULL, update_time = transaction_timestamp()
                        WHERE id = ? AND ingest_fence = ?
                          AND active_run_id IS NOT DISTINCT FROM ?
                        """,
                        run.runId(),
                        run.runId(),
                        run.documentId(),
                        run.fencingToken(),
                        run.expectedActiveRunId());
        if (switched != 1) {
            throw new IllegalStateException("知识代际发布 CAS 失败");
        }
        var published =
                jdbcTemplate.update(
                        """
                        UPDATE ai_knowledge_ingest_run
                        SET status = 'PUBLISHED', published_at = transaction_timestamp(),
                            finished_at = transaction_timestamp()
                        WHERE id = ? AND status = 'READY'
                        """,
                        run.runId());
        if (published != 1) {
            throw new IllegalStateException("知识代际发布状态切换失败");
        }
        var watermark = incrementProjectionWatermark(run.knowledgeBaseId());
        if (previousRunId != null && !previousRunId.equals(run.runId())) {
            insertRevocationEvent(
                    "document:" + run.documentId() + ":supersede:" + previousRunId,
                    run.knowledgeBaseId(),
                    previousRunId,
                    watermark);
        }
        jdbcTemplate.update(
                """
                INSERT INTO ai_knowledge_graph_outbox
                    (event_key, knowledge_base_id, aggregate_type, aggregate_id, run_id,
                     projection_watermark, event_type)
                SELECT 'fact:' || f.fact_key || ':run:' || CAST(? AS text), f.knowledge_base_id,
                       'FACT', f.id, ?, ?, 'FACT_UPSERT'
                FROM ai_knowledge_fact f
                WHERE EXISTS (
                    SELECT 1 FROM ai_knowledge_fact_evidence assertion
                    JOIN ai_knowledge_evidence evidence
                      ON evidence.id = assertion.evidence_id
                    WHERE assertion.fact_id = f.id
                      AND evidence.run_id = ?
                      AND assertion.recorded_at IS NOT NULL
                      AND assertion.expired_at IS NULL)
                ON CONFLICT (event_key) DO NOTHING
                """,
                run.runId(),
                run.runId(),
                watermark,
                run.runId());
        markProjectionDesired(run.knowledgeBaseId(), "PGVECTOR", watermark);
        refreshVectorCheckpoint(run.knowledgeBaseId(), watermark);
        markProjectionDesired(run.knowledgeBaseId(), "NEO4J", watermark);
        refreshGraphCheckpoint(run.knowledgeBaseId());
        return publicationReceipt(run);
    }

    @Transactional
    public boolean failRun(RunContext run, String errorMessage) {
        var current = lockDocument(run.documentId());
        if (!Objects.equals(current.activeRunId(), run.expectedActiveRunId())
                || current.ingestFence() != run.fencingToken()) {
            return false;
        }
        var failed =
                jdbcTemplate.update(
                        """
                        UPDATE ai_knowledge_ingest_run
                        SET status = 'FAILED', error_message = ?,
                            finished_at = transaction_timestamp()
                        WHERE id = ? AND document_id = ? AND run_no = ?
                          AND fencing_token = ?
                          AND expected_active_run_id IS NOT DISTINCT FROM ?
                          AND status IN ('PROCESSING', 'READY')
                        """,
                        truncate(errorMessage),
                        run.runId(),
                        run.documentId(),
                        run.runNo(),
                        run.fencingToken(),
                        run.expectedActiveRunId());
        if (failed != 1) {
            return false;
        }
        var documentFailed =
                jdbcTemplate.update(
                        """
                        UPDATE ai_knowledge_document
                        SET status = 3, chunk_count = 0, error_message = ?,
                            update_time = transaction_timestamp()
                        WHERE id = ? AND ingest_fence = ?
                          AND active_run_id IS NOT DISTINCT FROM ?
                        """,
                        truncate(errorMessage),
                        run.documentId(),
                        run.fencingToken(),
                        run.expectedActiveRunId());
        if (documentFailed != 1) {
            throw new IllegalStateException("知识代际失败状态 CAS 失败");
        }
        return true;
    }

    @Transactional
    public void revokeDocument(Long documentId) {
        var row =
                jdbcTemplate.queryForObject(
                        "SELECT knowledge_base_id, active_run_id FROM ai_knowledge_document WHERE id = ? FOR UPDATE",
                        (rs, rowNum) ->
                                new RevokedSource(rs.getLong(1), rs.getObject(2, UUID.class)),
                        documentId);
        if (row == null || row.activeRunId() == null) {
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE ai_knowledge_fact_evidence assertion
                SET expired_at = transaction_timestamp(),
                    expiration_reason = 'SOURCE_REVOKED'
                FROM ai_knowledge_evidence evidence
                WHERE assertion.evidence_id = evidence.id
                  AND evidence.run_id = ?
                  AND assertion.recorded_at IS NOT NULL
                  AND assertion.expired_at IS NULL
                """,
                row.activeRunId());
        jdbcTemplate.update(
                "UPDATE ai_knowledge_document SET active_run_id = NULL WHERE id = ?", documentId);
        jdbcTemplate.update(
                """
                UPDATE ai_knowledge_ingest_run
                SET status = 'SUPERSEDED', finished_at = transaction_timestamp()
                WHERE id = ? AND status = 'PUBLISHED'
                """,
                row.activeRunId());
        var watermark = incrementProjectionWatermark(row.knowledgeBaseId());
        insertRevocationEvent(
                "document:" + documentId + ":revoke:" + row.activeRunId(),
                row.knowledgeBaseId(),
                row.activeRunId(),
                watermark);
        markProjectionDesired(row.knowledgeBaseId(), "PGVECTOR", watermark);
        refreshVectorCheckpoint(row.knowledgeBaseId(), watermark);
        markProjectionDesired(row.knowledgeBaseId(), "NEO4J", watermark);
    }

    private PublishState lockDocument(Long documentId) {
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject(
                        "SELECT active_run_id, ingest_fence FROM ai_knowledge_document WHERE id = ? FOR UPDATE",
                        (rs, rowNum) ->
                                new PublishState(rs.getObject(1, UUID.class), rs.getLong(2)),
                        documentId));
    }

    private void requirePublishFence(RunContext run, PublishState current) {
        if (!Objects.equals(current.activeRunId(), run.expectedActiveRunId())
                || current.ingestFence() != run.fencingToken()) {
            throw new IllegalStateException("知识代际发布 fencing 校验失败，拒绝迟到任务回切");
        }
    }

    private String runStatus(RunContext run) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM ai_knowledge_ingest_run
                WHERE id = ? AND document_id = ? AND knowledge_base_id = ? AND run_no = ?
                  AND fencing_token = ?
                  AND expected_active_run_id IS NOT DISTINCT FROM ?
                """,
                String.class,
                run.runId(),
                run.documentId(),
                run.knowledgeBaseId(),
                run.runNo(),
                run.fencingToken(),
                run.expectedActiveRunId());
    }

    private PublishReceipt publicationReceipt(RunContext run) {
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject(
                        """
                        SELECT (SELECT COUNT(*) FROM ai_knowledge_chunk chunk
                                WHERE chunk.run_id = ?) AS chunk_count,
                               (SELECT COUNT(DISTINCT assertion.fact_id)
                                FROM ai_knowledge_fact_evidence assertion
                                JOIN ai_knowledge_evidence evidence
                                  ON evidence.id = assertion.evidence_id
                                WHERE evidence.run_id = ?) AS fact_count,
                               base.projection_watermark
                        FROM ai_knowledge_base base
                        WHERE base.id = ?
                        """,
                        (rs, rowNum) ->
                                new PublishReceipt(rs.getInt(1), rs.getInt(2), rs.getLong(3)),
                        run.runId(),
                        run.runId(),
                        run.knowledgeBaseId()));
    }

    private long incrementProjectionWatermark(Long knowledgeBaseId) {
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject(
                        """
                        UPDATE ai_knowledge_base
                        SET projection_watermark = projection_watermark + 1
                        WHERE id = ? AND deleted = false
                        RETURNING projection_watermark
                        """,
                        Long.class,
                        knowledgeBaseId));
    }

    private void insertRevocationEvent(
            String eventKey, Long knowledgeBaseId, UUID runId, long watermark) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_knowledge_graph_outbox
                    (event_key, knowledge_base_id, aggregate_type, aggregate_id, run_id,
                     projection_watermark, event_type)
                VALUES (?, ?, 'DOCUMENT', ?, ?, ?, 'DOCUMENT_REVOKED')
                ON CONFLICT (event_key) DO NOTHING
                """,
                eventKey,
                knowledgeBaseId,
                runId,
                runId,
                watermark);
    }

    private void markProjectionDesired(
            Long knowledgeBaseId, String projectionKind, long watermark) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_knowledge_projection_checkpoint
                    (knowledge_base_id, projection_kind, desired_watermark,
                     applied_watermark, status)
                VALUES (?, ?, ?, 0, 'DEGRADED')
                ON CONFLICT (knowledge_base_id, projection_kind) DO UPDATE SET
                    desired_watermark = GREATEST(
                        ai_knowledge_projection_checkpoint.desired_watermark,
                        EXCLUDED.desired_watermark),
                    status = 'DEGRADED', error_message = NULL,
                    updated_at = CURRENT_TIMESTAMP
                """,
                knowledgeBaseId,
                projectionKind,
                watermark);
    }

    private void refreshVectorCheckpoint(Long knowledgeBaseId, long watermark) {
        jdbcTemplate.update(
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
                       WHERE embedding.knowledge_base_pk = base.id))
                  AND NOT EXISTS (
                      (SELECT DISTINCT embedding.chunk_id
                       FROM ai_knowledge_embedding embedding
                       JOIN ai_knowledge_document document
                         ON document.id = embedding.document_id
                        AND document.active_run_id = embedding.run_id
                        AND document.deleted = false
                       WHERE embedding.knowledge_base_pk = base.id)
                      EXCEPT
                      (SELECT chunk.stable_id
                       FROM ai_knowledge_chunk chunk
                       JOIN ai_knowledge_document document
                         ON document.id = chunk.document_id
                        AND document.active_run_id = chunk.run_id
                        AND document.deleted = false
                       WHERE chunk.knowledge_base_id = base.id))
                """,
                watermark,
                knowledgeBaseId,
                watermark,
                watermark);
    }

    public List<SearchCandidate> keywordSearch(
            String query, Set<UUID> knowledgeBaseIds, SourceFilters filters, int topK) {
        if (knowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT c.stable_id, c.content,
                       ts_rank(to_tsvector('simple', c.content), plainto_tsquery('simple', ?)) AS rank
                FROM ai_knowledge_chunk c
                JOIN ai_knowledge_document d ON d.id = c.document_id AND d.active_run_id = c.run_id
                JOIN ai_knowledge_base b ON b.id = c.knowledge_base_id AND b.deleted = false
                WHERE b.stable_id = ANY(CAST(? AS uuid[]))
                  AND d.deleted = false
                  AND (CAST(? AS text[]) = '{}'::text[] OR d.source_type = ANY(CAST(? AS text[])))
                  AND (CAST(? AS text[]) = '{}'::text[] OR d.source_key = ANY(CAST(? AS text[])))
                  AND (CAST(? AS uuid[]) = '{}'::uuid[] OR d.stable_id = ANY(CAST(? AS uuid[])))
                  AND to_tsvector('simple', c.content) @@ plainto_tsquery('simple', ?)
                ORDER BY rank DESC, c.stable_id
                LIMIT ?
                """,
                (rs, rowNum) ->
                        new SearchCandidate(
                                rs.getObject(1, UUID.class), rs.getString(2), rs.getDouble(3)),
                query,
                uuidArray(knowledgeBaseIds),
                textArray(filters.sourceTypes()),
                textArray(filters.sourceTypes()),
                textArray(filters.sourceKeys()),
                textArray(filters.sourceKeys()),
                uuidArray(filters.documentIds()),
                uuidArray(filters.documentIds()),
                query,
                topK);
    }

    public List<GraphCandidate> graphCandidates(
            Set<String> factKeys, Set<UUID> knowledgeBaseIds, SourceFilters filters, int topK) {
        if (factKeys.isEmpty() || knowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT c.stable_id, c.content, f.id, e.id
                FROM ai_knowledge_fact f
                JOIN ai_knowledge_fact_evidence assertion ON assertion.fact_id = f.id
                JOIN ai_knowledge_evidence e ON e.id = assertion.evidence_id
                JOIN ai_knowledge_chunk c ON c.stable_id = e.focus_chunk_id
                JOIN ai_knowledge_document d ON d.id = c.document_id AND d.active_run_id = e.run_id
                JOIN ai_knowledge_base b ON b.id = f.knowledge_base_id
                WHERE f.fact_key = ANY(string_to_array(?, ','))
                  AND b.stable_id = ANY(CAST(? AS uuid[]))
                  AND d.deleted = false
                  AND assertion.recorded_at IS NOT NULL
                  AND assertion.expired_at IS NULL
                  AND (assertion.valid_at IS NULL
                       OR assertion.valid_at <= CURRENT_TIMESTAMP)
                  AND (assertion.invalid_at IS NULL
                       OR assertion.invalid_at > CURRENT_TIMESTAMP)
                  AND (CAST(? AS text[]) = '{}'::text[] OR d.source_type = ANY(CAST(? AS text[])))
                  AND (CAST(? AS text[]) = '{}'::text[] OR d.source_key = ANY(CAST(? AS text[])))
                  AND (CAST(? AS uuid[]) = '{}'::uuid[] OR d.stable_id = ANY(CAST(? AS uuid[])))
                ORDER BY f.confidence DESC, c.stable_id
                LIMIT ?
                """,
                (rs, rowNum) ->
                        new GraphCandidate(
                                rs.getObject(1, UUID.class),
                                rs.getString(2),
                                rs.getObject(3, UUID.class),
                                rs.getObject(4, UUID.class)),
                String.join(",", factKeys),
                uuidArray(knowledgeBaseIds),
                textArray(filters.sourceTypes()),
                textArray(filters.sourceTypes()),
                textArray(filters.sourceKeys()),
                textArray(filters.sourceKeys()),
                uuidArray(filters.documentIds()),
                uuidArray(filters.documentIds()),
                topK);
    }

    public Optional<SourceRef> sourceRef(
            UUID chunkId,
            Set<UUID> factIds,
            Set<UUID> evidenceIds,
            Set<UUID> authorizedKnowledgeBaseIds,
            SourceFilters filters) {
        if (authorizedKnowledgeBaseIds.isEmpty()) {
            return Optional.empty();
        }
        return jdbcTemplate
                .query(
                        """
                        SELECT b.stable_id, b.name, b.visibility, d.stable_id, d.source_type,
                               d.source_key, d.source_uri, c.run_id, c.stable_id,
                               validated.fact_ids, validated.evidence_ids
                        FROM ai_knowledge_chunk c
                        JOIN ai_knowledge_document d ON d.id = c.document_id
                            AND d.active_run_id = c.run_id AND d.deleted = false
                        JOIN ai_knowledge_base b ON b.id = c.knowledge_base_id AND b.deleted = false
                        LEFT JOIN LATERAL (
                            SELECT array_agg(DISTINCT fe.fact_id) AS fact_ids,
                                   array_agg(DISTINCT e.id) AS evidence_ids
                            FROM ai_knowledge_fact_evidence fe
                            JOIN ai_knowledge_fact f
                              ON f.id = fe.fact_id
                             AND f.knowledge_base_id = c.knowledge_base_id
                            JOIN ai_knowledge_evidence e
                              ON e.id = fe.evidence_id
                             AND e.knowledge_base_id = c.knowledge_base_id
                             AND e.document_id = c.document_id
                             AND e.run_id = c.run_id
                             AND e.focus_chunk_id = c.stable_id
                            WHERE fe.fact_id = ANY(CAST(? AS uuid[]))
                              AND e.id = ANY(CAST(? AS uuid[]))
                              AND fe.recorded_at IS NOT NULL
                              AND fe.expired_at IS NULL
                              AND (fe.valid_at IS NULL OR fe.valid_at <= CURRENT_TIMESTAMP)
                              AND (fe.invalid_at IS NULL OR fe.invalid_at > CURRENT_TIMESTAMP)
                        ) validated ON TRUE
                        WHERE c.stable_id = ?
                          AND b.stable_id = ANY(CAST(? AS uuid[]))
                          AND (CAST(? AS text[]) = '{}'::text[] OR d.source_type = ANY(CAST(? AS text[])))
                          AND (CAST(? AS text[]) = '{}'::text[] OR d.source_key = ANY(CAST(? AS text[])))
                          AND (CAST(? AS uuid[]) = '{}'::uuid[] OR d.stable_id = ANY(CAST(? AS uuid[])))
                          AND ((cardinality(CAST(? AS uuid[])) = 0
                                AND cardinality(CAST(? AS uuid[])) = 0)
                               OR validated.fact_ids IS NOT NULL)
                        """,
                        (rs, rowNum) ->
                                new SourceRef(
                                        rs.getObject(1, UUID.class),
                                        rs.getString(2),
                                        Visibility.valueOf(rs.getString(3)),
                                        rs.getObject(4, UUID.class),
                                        rs.getString(5),
                                        rs.getString(6),
                                        rs.getString(7),
                                        rs.getObject(8, UUID.class),
                                        rs.getObject(9, UUID.class),
                                        uuidSet(rs.getArray(10)),
                                        uuidSet(rs.getArray(11))),
                        uuidArray(factIds),
                        uuidArray(evidenceIds),
                        chunkId,
                        uuidArray(authorizedKnowledgeBaseIds),
                        textArray(filters.sourceTypes()),
                        textArray(filters.sourceTypes()),
                        textArray(filters.sourceKeys()),
                        textArray(filters.sourceKeys()),
                        uuidArray(filters.documentIds()),
                        uuidArray(filters.documentIds()),
                        uuidArray(factIds),
                        uuidArray(evidenceIds))
                .stream()
                .findFirst();
    }

    public List<UUID> currentFactIdsForRunExclusion(UUID revokedRunId) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT f.id
                FROM ai_knowledge_fact f
                JOIN ai_knowledge_fact_evidence fe ON fe.fact_id = f.id
                JOIN ai_knowledge_evidence revoked ON revoked.id = fe.evidence_id
                WHERE revoked.run_id = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM ai_knowledge_fact_evidence current_fe
                      JOIN ai_knowledge_evidence current_e ON current_e.id = current_fe.evidence_id
                      JOIN ai_knowledge_document current_d
                        ON current_d.active_run_id = current_e.run_id AND current_d.deleted = false
                      WHERE current_fe.fact_id = f.id
                        AND current_fe.recorded_at IS NOT NULL
                        AND current_fe.expired_at IS NULL
                        AND (current_fe.valid_at IS NULL
                             OR current_fe.valid_at <= CURRENT_TIMESTAMP)
                        AND (current_fe.invalid_at IS NULL
                             OR current_fe.invalid_at > CURRENT_TIMESTAMP))
                ORDER BY f.id
                """,
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                revokedRunId);
    }

    public List<UUID> currentFactIds(UUID knowledgeBaseId) {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT f.id
                FROM ai_knowledge_fact f
                JOIN ai_knowledge_base b ON b.id = f.knowledge_base_id
                JOIN ai_knowledge_fact_evidence assertion ON assertion.fact_id = f.id
                JOIN ai_knowledge_evidence e ON e.id = assertion.evidence_id
                JOIN ai_knowledge_document d ON d.active_run_id = e.run_id AND d.deleted = false
                WHERE b.stable_id = ?
                  AND assertion.recorded_at IS NOT NULL
                  AND assertion.expired_at IS NULL
                  AND (assertion.valid_at IS NULL
                       OR assertion.valid_at <= CURRENT_TIMESTAMP)
                  AND (assertion.invalid_at IS NULL
                       OR assertion.invalid_at > CURRENT_TIMESTAMP)
                ORDER BY f.id
                """,
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                knowledgeBaseId);
    }

    public List<UUID> factIds(UUID knowledgeBaseId, KnowledgeTemporalScope scope) {
        Objects.requireNonNull(scope, "scope 不能为空");
        return jdbcTemplate.query(
                """
                SELECT DISTINCT f.id
                FROM ai_knowledge_fact f
                JOIN ai_knowledge_base b ON b.id = f.knowledge_base_id
                JOIN ai_knowledge_fact_evidence assertion ON assertion.fact_id = f.id
                JOIN ai_knowledge_evidence e ON e.id = assertion.evidence_id
                JOIN ai_knowledge_document d ON d.id = e.document_id AND d.deleted = false
                WHERE b.stable_id = ?
                  AND (assertion.valid_at IS NULL OR assertion.valid_at <= ?)
                  AND (assertion.invalid_at IS NULL OR assertion.invalid_at > ?)
                  AND assertion.recorded_at <= ?
                  AND (assertion.expired_at IS NULL OR assertion.expired_at > ?)
                ORDER BY f.id
                """,
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                knowledgeBaseId,
                scope.validAt(),
                scope.validAt(),
                scope.knownAt(),
                scope.knownAt());
    }

    public Optional<FactProjection> currentFactProjection(UUID factId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT f.id, f.fact_key, b.stable_id, f.predicate, f.confidence,

                               s.id, s.canonical_name, s.entity_type, s.description,
                               o.id, o.canonical_name, o.entity_type, o.description,
                               array_agg(DISTINCT e.id),
                               array_agg(DISTINCT d.source_type),
                               array_remove(array_agg(DISTINCT d.source_key), NULL),
                               array_agg(DISTINCT d.stable_id)
                        FROM ai_knowledge_fact f
                        JOIN ai_knowledge_base b ON b.id = f.knowledge_base_id
                        JOIN ai_knowledge_entity s ON s.id = f.subject_entity_id
                        JOIN ai_knowledge_entity o ON o.id = f.object_entity_id
                        JOIN ai_knowledge_fact_evidence assertion ON assertion.fact_id = f.id
                        JOIN ai_knowledge_evidence e ON e.id = assertion.evidence_id
                        JOIN ai_knowledge_document d ON d.active_run_id = e.run_id AND d.deleted = false
                        WHERE f.id = ? AND f.object_kind = 'ENTITY'
                          AND assertion.recorded_at IS NOT NULL
                          AND assertion.expired_at IS NULL
                          AND (assertion.valid_at IS NULL
                               OR assertion.valid_at <= CURRENT_TIMESTAMP)
                          AND (assertion.invalid_at IS NULL
                               OR assertion.invalid_at > CURRENT_TIMESTAMP)
                        GROUP BY f.id, b.stable_id, s.id, o.id
                        """,
                        (rs, rowNum) -> mapFactProjection(rs),
                        factId)
                .stream()
                .findFirst();
    }

    public Set<UUID> currentEntityIds(UUID knowledgeBaseId) {
        return Set.copyOf(
                jdbcTemplate.query(
                        """
                        SELECT DISTINCT entity_id
                        FROM (
                            SELECT f.subject_entity_id AS entity_id
                            FROM ai_knowledge_fact f
                            JOIN ai_knowledge_base b ON b.id = f.knowledge_base_id
                            JOIN ai_knowledge_fact_evidence assertion ON assertion.fact_id = f.id
                            JOIN ai_knowledge_evidence e ON e.id = assertion.evidence_id
                            JOIN ai_knowledge_document d
                              ON d.active_run_id = e.run_id AND d.deleted = false
                            WHERE b.stable_id = ? AND f.object_kind = 'ENTITY'
                              AND assertion.recorded_at IS NOT NULL
                              AND assertion.expired_at IS NULL
                              AND (assertion.valid_at IS NULL
                                   OR assertion.valid_at <= CURRENT_TIMESTAMP)
                              AND (assertion.invalid_at IS NULL
                                   OR assertion.invalid_at > CURRENT_TIMESTAMP)
                            UNION
                            SELECT f.object_entity_id AS entity_id
                            FROM ai_knowledge_fact f
                            JOIN ai_knowledge_base b ON b.id = f.knowledge_base_id
                            JOIN ai_knowledge_fact_evidence assertion ON assertion.fact_id = f.id
                            JOIN ai_knowledge_evidence e ON e.id = assertion.evidence_id
                            JOIN ai_knowledge_document d
                              ON d.active_run_id = e.run_id AND d.deleted = false
                            WHERE b.stable_id = ? AND f.object_kind = 'ENTITY'
                              AND assertion.recorded_at IS NOT NULL
                              AND assertion.expired_at IS NULL
                              AND (assertion.valid_at IS NULL
                                   OR assertion.valid_at <= CURRENT_TIMESTAMP)
                              AND (assertion.invalid_at IS NULL
                                   OR assertion.invalid_at > CURRENT_TIMESTAMP)
                        ) current_entities
                        ORDER BY entity_id
                        """,
                        (rs, rowNum) -> rs.getObject(1, UUID.class),
                        knowledgeBaseId,
                        knowledgeBaseId));
    }

    public Optional<GraphProjectionStatus> graphProjectionStatus(UUID knowledgeBaseId) {
        return jdbcTemplate
                .query(
                        """
                        SELECT base.stable_id, base.projection_watermark,
                               COALESCE(checkpoint.desired_watermark, base.projection_watermark),
                               COALESCE(checkpoint.applied_watermark, 0),
                               COALESCE(checkpoint.status, 'NOT_INITIALIZED'),
                               checkpoint.rebuild_request_key, checkpoint.error_message,
                               checkpoint.updated_at
                        FROM ai_knowledge_base base
                        LEFT JOIN ai_knowledge_projection_checkpoint checkpoint
                          ON checkpoint.knowledge_base_id = base.id
                         AND checkpoint.projection_kind = 'NEO4J'
                        WHERE base.stable_id = ? AND base.deleted = false
                        """,
                        (rs, rowNum) -> {
                            var baseWatermark = rs.getLong(2);
                            var desiredWatermark = rs.getLong(3);
                            var appliedWatermark = rs.getLong(4);
                            var status = rs.getString(5);
                            return new GraphProjectionStatus(
                                    rs.getObject(1, UUID.class),
                                    baseWatermark,
                                    desiredWatermark,
                                    appliedWatermark,
                                    status,
                                    rs.getString(6),
                                    rs.getString(7),
                                    rs.getObject(8, LocalDateTime.class),
                                    "READY".equals(status)
                                            && desiredWatermark == appliedWatermark
                                            && desiredWatermark == baseWatermark);
                        },
                        knowledgeBaseId)
                .stream()
                .findFirst();
    }

    public boolean isProjectionReady(UUID knowledgeBaseId, String projectionKind) {
        return Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(
                        """
                        SELECT checkpoint.status = 'READY'
                           AND checkpoint.desired_watermark = checkpoint.applied_watermark
                           AND checkpoint.desired_watermark = base.projection_watermark
                        FROM ai_knowledge_projection_checkpoint checkpoint
                        JOIN ai_knowledge_base base
                          ON base.id = checkpoint.knowledge_base_id AND base.deleted = false
                        WHERE base.stable_id = ? AND checkpoint.projection_kind = ?
                        """,
                        Boolean.class,
                        knowledgeBaseId,
                        projectionKind));
    }

    public ProjectionSnapshot projectionSnapshot(UUID knowledgeBaseId) {
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject(
                        """
                        SELECT id, stable_id, projection_watermark
                        FROM ai_knowledge_base
                        WHERE stable_id = ? AND deleted = false
                        """,
                        (rs, rowNum) ->
                                new ProjectionSnapshot(
                                        rs.getLong(1), rs.getObject(2, UUID.class), rs.getLong(3)),
                        knowledgeBaseId));
    }

    public ProjectionRebuild beginGraphRebuild(UUID knowledgeBaseId, String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            throw new IllegalArgumentException("图投影 rebuild requestKey 不能为空");
        }
        var normalizedRequestKey = requestKey.trim();
        if (normalizedRequestKey.length() > 200) {
            throw new IllegalArgumentException("图投影 rebuild requestKey 不能超过 200 个字符");
        }
        var snapshot = projectionSnapshot(knowledgeBaseId);
        var acquired =
                jdbcTemplate.query(
                        """
                        INSERT INTO ai_knowledge_projection_checkpoint
                            (knowledge_base_id, projection_kind, desired_watermark,
                             applied_watermark, rebuild_request_key, status)
                        VALUES (?, 'NEO4J', ?, 0, ?, 'REBUILDING')
                        ON CONFLICT (knowledge_base_id, projection_kind) DO UPDATE SET
                            desired_watermark = EXCLUDED.desired_watermark,
                            rebuild_request_key = EXCLUDED.rebuild_request_key,
                            status = 'REBUILDING', error_message = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE ai_knowledge_projection_checkpoint.status <> 'REBUILDING'
                          AND ai_knowledge_projection_checkpoint.desired_watermark
                              <= EXCLUDED.desired_watermark
                          AND NOT (
                              ai_knowledge_projection_checkpoint.status = 'READY'
                              AND ai_knowledge_projection_checkpoint.rebuild_request_key
                                  = EXCLUDED.rebuild_request_key
                              AND ai_knowledge_projection_checkpoint.desired_watermark
                                  = EXCLUDED.desired_watermark
                              AND ai_knowledge_projection_checkpoint.applied_watermark
                                  = EXCLUDED.desired_watermark)
                        RETURNING desired_watermark, applied_watermark, status,
                                  rebuild_request_key
                        """,
                        (rs, rowNum) ->
                                new ProjectionCheckpoint(
                                        rs.getLong(1),
                                        rs.getLong(2),
                                        rs.getString(3),
                                        rs.getString(4)),
                        snapshot.knowledgeBaseId(),
                        snapshot.watermark(),
                        normalizedRequestKey);
        if (!acquired.isEmpty()) {
            return new ProjectionRebuild(snapshot, normalizedRequestKey, true);
        }
        return new ProjectionRebuild(snapshot, normalizedRequestKey, false);
    }

    @Transactional
    public boolean completeGraphRebuild(
            ProjectionRebuild rebuild, boolean projectionSetsMatch, String errorMessage) {
        var snapshot = rebuild.snapshot();
        var owned =
                jdbcTemplate.update(
                        """
                        UPDATE ai_knowledge_projection_checkpoint
                        SET updated_at = updated_at
                        WHERE knowledge_base_id = ? AND projection_kind = 'NEO4J'
                          AND rebuild_request_key = ? AND status = 'REBUILDING'
                          AND desired_watermark = ?
                        """,
                        snapshot.knowledgeBaseId(),
                        rebuild.requestKey(),
                        snapshot.watermark());
        if (owned != 1) {
            return false;
        }
        var currentWatermark =
                Objects.requireNonNull(
                        jdbcTemplate.queryForObject(
                                "SELECT projection_watermark FROM ai_knowledge_base WHERE id = ? FOR UPDATE",
                                Long.class,
                                snapshot.knowledgeBaseId()));
        if (!projectionSetsMatch || currentWatermark != snapshot.watermark()) {
            jdbcTemplate.update(
                    """
                    UPDATE ai_knowledge_projection_checkpoint
                    SET status = 'DEGRADED', error_message = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE knowledge_base_id = ? AND projection_kind = 'NEO4J'
                      AND rebuild_request_key = ? AND status = 'REBUILDING'
                      AND desired_watermark = ?
                    """,
                    truncate(errorMessage == null ? "重建期间知识库水位变化或投影集合不一致" : errorMessage),
                    snapshot.knowledgeBaseId(),
                    rebuild.requestKey(),
                    snapshot.watermark());
            return false;
        }
        jdbcTemplate.update(
                """
                UPDATE ai_knowledge_graph_outbox
                SET status = 'DONE', error_message = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE knowledge_base_id = ? AND projection_watermark <= ?
                  AND status <> 'DONE'
                """,
                snapshot.knowledgeBaseId(),
                snapshot.watermark());
        var updated =
                jdbcTemplate.update(
                        """
                        UPDATE ai_knowledge_projection_checkpoint checkpoint
                        SET desired_watermark = ?, applied_watermark = ?, status = 'READY',
                            error_message = NULL, updated_at = CURRENT_TIMESTAMP
                        WHERE checkpoint.knowledge_base_id = ?
                          AND checkpoint.projection_kind = 'NEO4J'
                          AND checkpoint.rebuild_request_key = ?
                          AND checkpoint.status = 'REBUILDING'
                          AND checkpoint.desired_watermark = ?
                          AND NOT EXISTS (
                              SELECT 1 FROM ai_knowledge_graph_outbox outbox
                              WHERE outbox.knowledge_base_id = checkpoint.knowledge_base_id
                                AND outbox.projection_watermark <= ?
                                AND outbox.status <> 'DONE')
                        """,
                        snapshot.watermark(),
                        snapshot.watermark(),
                        snapshot.knowledgeBaseId(),
                        rebuild.requestKey(),
                        snapshot.watermark(),
                        snapshot.watermark());
        return updated == 1;
    }

    public List<OutboxEvent> claimOutbox(int limit) {
        return jdbcTemplate.query(
                """
                UPDATE ai_knowledge_graph_outbox o
                SET status = 'PROCESSING', attempts = attempts + 1, updated_at = CURRENT_TIMESTAMP
                FROM (
                    SELECT event_id FROM ai_knowledge_graph_outbox
                    WHERE status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                ) picked
                WHERE o.event_id = picked.event_id
                RETURNING o.event_id, o.aggregate_id, o.event_type, o.knowledge_base_id,
                          o.run_id, o.projection_watermark, o.attempts
                """,
                (rs, rowNum) ->
                        new OutboxEvent(
                                rs.getObject(1, UUID.class),
                                rs.getObject(2, UUID.class),
                                rs.getString(3),
                                rs.getLong(4),
                                rs.getObject(5, UUID.class),
                                rs.getLong(6),
                                rs.getInt(7)),
                limit);
    }

    @Transactional
    public void completeOutbox(OutboxEvent event) {
        jdbcTemplate.update(
                "UPDATE ai_knowledge_graph_outbox SET status = 'DONE', error_message = NULL, updated_at = CURRENT_TIMESTAMP WHERE event_id = ? AND status = 'PROCESSING'",
                event.eventId());
        refreshGraphCheckpoint(event.knowledgeBaseId());
    }

    @Transactional
    public void failOutbox(OutboxEvent event, String error) {
        var dead = event.attempts() >= 8;
        jdbcTemplate.update(
                """
                UPDATE ai_knowledge_graph_outbox
                SET status = ?, error_message = ?,
                    available_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 minute'),
                    updated_at = CURRENT_TIMESTAMP
                WHERE event_id = ? AND status = 'PROCESSING'
                """,
                dead ? "DEAD" : "PENDING",
                truncate(error),
                Math.min(60, 1 << Math.min(event.attempts(), 5)),
                event.eventId());
        refreshGraphCheckpoint(event.knowledgeBaseId());
    }

    void refreshGraphCheckpoint(Long knowledgeBaseId) {
        var counts =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FILTER (WHERE outbox.status <> 'DONE') AS unfinished,
                               COUNT(*) FILTER (WHERE outbox.status = 'DEAD') AS dead
                        FROM ai_knowledge_projection_checkpoint checkpoint
                        LEFT JOIN ai_knowledge_graph_outbox outbox
                          ON outbox.knowledge_base_id = checkpoint.knowledge_base_id
                         AND outbox.projection_watermark > checkpoint.applied_watermark
                         AND outbox.projection_watermark <= checkpoint.desired_watermark
                        WHERE checkpoint.knowledge_base_id = ?
                          AND checkpoint.projection_kind = 'NEO4J'
                        """,
                        (rs, rowNum) -> new ProjectionEventCounts(rs.getLong(1), rs.getLong(2)),
                        knowledgeBaseId);
        if (counts == null) {
            return;
        }
        var ready = counts.unfinished() == 0 && counts.dead() == 0;
        jdbcTemplate.update(
                """
                UPDATE ai_knowledge_projection_checkpoint
                SET applied_watermark = CASE WHEN ? THEN desired_watermark ELSE applied_watermark END,
                    status = CASE WHEN ? THEN 'READY' ELSE 'DEGRADED' END,
                    error_message = CASE WHEN ? THEN NULL
                                         WHEN ? > 0 THEN '存在 DEAD 图投影事件'
                                         ELSE '图投影事件尚未完成' END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE knowledge_base_id = ? AND projection_kind = 'NEO4J'
                """,
                ready,
                ready,
                ready,
                counts.dead(),
                knowledgeBaseId);
    }

    private FactProjection mapFactProjection(ResultSet rs) throws SQLException {
        return new FactProjection(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getObject(3, UUID.class),
                rs.getString(4),
                rs.getDouble(5),
                new ProjectedEntity(
                        rs.getObject(6, UUID.class),
                        rs.getString(7),
                        rs.getString(8),
                        rs.getString(9)),
                new ProjectedEntity(
                        rs.getObject(10, UUID.class),
                        rs.getString(11),
                        rs.getString(12),
                        rs.getString(13)),
                uuidSet(rs.getArray(14)),
                stringSet(rs.getArray(15)),
                stringSet(rs.getArray(16)),
                uuidSet(rs.getArray(17)));
    }

    private Set<UUID> uuidSet(java.sql.Array array) throws SQLException {
        var values = new LinkedHashSet<UUID>();
        if (array != null) {
            for (var value : (Object[]) array.getArray()) {
                if (value != null) {
                    values.add(
                            value instanceof UUID uuid ? uuid : UUID.fromString(value.toString()));
                }
            }
        }
        return Set.copyOf(values);
    }

    private Set<String> stringSet(java.sql.Array array) throws SQLException {
        var values = new LinkedHashSet<String>();
        if (array != null) {
            for (var value : (Object[]) array.getArray()) {
                if (value != null) {
                    values.add(value.toString());
                }
            }
        }
        return Set.copyOf(values);
    }

    public void upsertEntityEmbedding(
            UUID entityId, Long knowledgeBaseId, UUID runId, String modelId, float[] embedding) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_knowledge_entity_embedding
                    (entity_id, knowledge_base_id, source_run_id, model_id, embedding)
                VALUES (?, ?, ?, ?, CAST(? AS vector))
                ON CONFLICT (entity_id, model_id) DO UPDATE SET
                    source_run_id = EXCLUDED.source_run_id,
                    embedding = EXCLUDED.embedding,
                    updated_at = CURRENT_TIMESTAMP
                """,
                entityId,
                knowledgeBaseId,
                runId,
                modelId,
                vector(embedding));
    }

    public void addAlias(
            Long knowledgeBaseId,
            UUID entityId,
            UUID runId,
            String normalizedAlias,
            double confidence) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_knowledge_entity_alias
                    (knowledge_base_id, entity_id, normalized_alias, confidence, source_run_id)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (knowledge_base_id, normalized_alias, entity_id) DO UPDATE SET
                    confidence = GREATEST(ai_knowledge_entity_alias.confidence, EXCLUDED.confidence)
                """,
                knowledgeBaseId,
                entityId,
                normalizedAlias,
                confidence,
                runId);
    }

    public static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境缺少 SHA-256", failure);
        }
    }

    private Integer number(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String textArray(Iterable<String> values) {
        var items = new ArrayList<String>();
        values.forEach(value -> items.add(value.replace("\\", "\\\\").replace("\"", "\\\"")));
        if (items.isEmpty()) {
            return "{}";
        }
        return "{\"" + String.join("\",\"", items) + "\"}";
    }

    private String uuidArray(Iterable<UUID> values) {
        var items = new ArrayList<String>();
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

    private String truncate(String value) {
        return value == null || value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private record RunSource(
            Long ownerId, Long uploadedBy, String tenantId, UUID activeRunId, long ingestFence) {}

    private record StoredRun(
            UUID runId,
            long runNo,
            Long knowledgeBaseId,
            Long documentId,
            Long payerUserId,
            String tenantId,
            UUID expectedActiveRunId,
            long fencingToken,
            String status,
            String extractionSystemPrompt,
            String extractionPromptDigest,
            int extractionPromptVersion,
            String extractionUserPrompt,
            String extractionUserPromptDigest,
            int extractionUserPromptVersion,
            String extractionOutputContractVersion,
            String extractionModelId,
            String entityResolutionSystemPrompt,
            String entityResolutionPromptDigest,
            int entityResolutionPromptVersion,
            String entityResolutionUserPrompt,
            String entityResolutionUserPromptDigest,
            int entityResolutionUserPromptVersion,
            String entityResolutionOutputContractVersion,
            String entityResolutionModelId) {
        RunContext context() {
            return context(expectedActiveRunId, fencingToken, status);
        }

        RunContext context(UUID expectedActiveRun, long fence, String currentStatus) {
            return new RunContext(
                    runId,
                    runNo,
                    knowledgeBaseId,
                    documentId,
                    new BillingContext(tenantId, runId, payerUserId),
                    expectedActiveRun,
                    fence,
                    currentStatus,
                    extractionSystemPrompt,
                    extractionPromptDigest,
                    extractionPromptVersion,
                    extractionUserPrompt,
                    extractionUserPromptDigest,
                    extractionUserPromptVersion,
                    extractionOutputContractVersion,
                    extractionModelId,
                    entityResolutionSystemPrompt,
                    entityResolutionPromptDigest,
                    entityResolutionPromptVersion,
                    entityResolutionUserPrompt,
                    entityResolutionUserPromptDigest,
                    entityResolutionUserPromptVersion,
                    entityResolutionOutputContractVersion,
                    entityResolutionModelId);
        }
    }

    private record PublishState(UUID activeRunId, long ingestFence) {}

    private record RevokedSource(Long knowledgeBaseId, UUID activeRunId) {}

    public record RunContext(
            UUID runId,
            long runNo,
            Long knowledgeBaseId,
            Long documentId,
            BillingContext billing,
            UUID expectedActiveRunId,
            long fencingToken,
            String status,
            String extractionSystemPrompt,
            String extractionPromptDigest,
            int extractionPromptVersion,
            String extractionUserPrompt,
            String extractionUserPromptDigest,
            int extractionUserPromptVersion,
            String extractionOutputContractVersion,
            String extractionModelId,
            String entityResolutionSystemPrompt,
            String entityResolutionPromptDigest,
            int entityResolutionPromptVersion,
            String entityResolutionUserPrompt,
            String entityResolutionUserPromptDigest,
            int entityResolutionUserPromptVersion,
            String entityResolutionOutputContractVersion,
            String entityResolutionModelId) {}

    public record StoredChunk(
            UUID stableId,
            int index,
            String content,
            String contentHash,
            int tokenCount,
            Map<String, Object> metadata) {}

    public record EntityCandidate(UUID id, String name, String type, String description) {}

    public record FactAssertion(
            java.time.Instant validAt,
            java.time.Instant invalidAt,
            Map<String, Object> attributes) {

        public FactAssertion {
            if (validAt != null && invalidAt != null && !validAt.isBefore(invalidAt)) {
                throw new IllegalArgumentException("validAt 必须早于 invalidAt");
            }
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    public record PersistedFact(UUID factId, UUID evidenceId, String factKey) {}

    public record PublishReceipt(int chunkCount, int factCount, long projectionWatermark) {}

    public record SearchCandidate(UUID chunkId, String content, double score) {}

    public record GraphCandidate(UUID chunkId, String content, UUID factId, UUID evidenceId) {}

    public record ProjectedEntity(UUID id, String name, String type, String description) {}

    public record FactProjection(
            UUID factId,
            String factKey,
            UUID knowledgeBaseId,
            String predicate,
            double confidence,
            ProjectedEntity subject,
            ProjectedEntity object,
            Set<UUID> evidenceIds,
            Set<String> sourceTypes,
            Set<String> sourceKeys,
            Set<UUID> documentIds) {}

    public record GraphProjectionStatus(
            UUID knowledgeBaseId,
            long baseWatermark,
            long desiredWatermark,
            long appliedWatermark,
            String status,
            String rebuildRequestKey,
            String errorMessage,
            LocalDateTime updatedAt,
            boolean ready) {}

    public record ProjectionSnapshot(Long knowledgeBaseId, UUID stableId, long watermark) {}

    public record ProjectionRebuild(
            ProjectionSnapshot snapshot, String requestKey, boolean execute) {}

    private record ProjectionCheckpoint(
            long desiredWatermark, long appliedWatermark, String status, String requestKey) {}

    private record ProjectionEventCounts(long unfinished, long dead) {}

    public record OutboxEvent(
            UUID eventId,
            UUID aggregateId,
            String eventType,
            Long knowledgeBaseId,
            UUID runId,
            long projectionWatermark,
            int attempts) {}
}
