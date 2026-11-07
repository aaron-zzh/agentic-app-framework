package com.xuejiai.aaf.framework.engine.knowledge.summary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.xuejiai.aaf.common.enums.knowledge.DocumentStatusEnum;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.knowledge.summary.KnowledgeSummaryPrompt.BaseSummaryOutput;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeIngestConfigurationService;
import com.xuejiai.aaf.framework.intelligent.ai.chat.DashScopeStructuredOutputClient;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识库涵盖主题汇总：定时扫描待重算的知识库，根据有效文档的标题与摘要生成 {@code ai_knowledge_base.ai_summary}。
 *
 * <p>涵盖主题只描述资料实际覆盖的主题，系统生成、只读；知识库说明（description）由用户填写，仅作为背景输入，系统永不修改。
 *
 * <p>待重算标记 {@code ai_summary_stale_at} 在文档发布或撤销时写入；距最近一次变更满 2 分钟且库内没有待处理/处理中文档时才汇总，按读取到的标记值 CAS
 * 清除，汇总期间又有文档变更时保留标记等待下一轮。
 *
 * <p>计费复用 {@link AiCreditGuard} 的预检与幂等结算，记给知识库拥有者，幂等键为知识库 ID + 待重算标记值；结算与写回在同一事务内。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseSummaryService {

    static final String STAGE = "knowledge-base-summary";

    private static final int BATCH_SIZE = 20;
    private static final int MAX_DOCUMENTS_INPUT_LENGTH = 30_000;
    private static final int MAX_TITLE_LENGTH = 200;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final KnowledgeIngestConfigurationService configurationService;
    private final DashScopeStructuredOutputClient structuredOutputClient;
    private final AiCreditGuard creditGuard;
    private final ModelManagementService modelManagementService;

    @Scheduled(
            fixedDelayString = "${aaf.knowledge.summary.scan-delay-ms:60000}",
            initialDelayString = "${aaf.knowledge.summary.initial-delay-ms:60000}")
    public void summarizePending() {
        List<StaleBase> candidates;
        try {
            candidates = findStaleBases();
        } catch (RuntimeException failure) {
            log.warn("扫描待重算知识库涵盖主题失败，原因={}", failure.getMessage());
            return;
        }
        for (var base : candidates) {
            try {
                summarize(base);
            } catch (RuntimeException failure) {
                log.warn(
                        "知识库涵盖主题生成失败，保留待重算标记，knowledgeBaseId={}, 原因={}",
                        base.id(),
                        failure.getMessage());
            }
        }
    }

    private List<StaleBase> findStaleBases() {
        return jdbcTemplate.query(
                """
                SELECT b.id, b.ai_summary_stale_at, b.name, b.description, b.owner_id,
                       COALESCE(CAST(b.org_id AS text), 'system')
                FROM ai_knowledge_base b
                WHERE b.deleted = false
                  AND b.ai_summary_stale_at IS NOT NULL
                  AND b.ai_summary_stale_at <= CURRENT_TIMESTAMP - INTERVAL '2 minutes'
                  AND NOT EXISTS (
                      SELECT 1 FROM ai_knowledge_document d
                      WHERE d.knowledge_base_id = b.id AND d.deleted = false
                        AND d.status IN (?, ?))
                ORDER BY b.ai_summary_stale_at
                LIMIT ?
                """,
                (rs, rowNum) ->
                        new StaleBase(
                                rs.getLong(1),
                                rs.getTimestamp(2),
                                rs.getString(3),
                                rs.getString(4),
                                rs.getObject(5, Long.class),
                                rs.getString(6)),
                DocumentStatusEnum.PENDING.getCode(),
                DocumentStatusEnum.PROCESSING.getCode(),
                BATCH_SIZE);
    }

    private void summarize(StaleBase base) {
        var documents = effectiveDocuments(base.id());
        if (documents.isEmpty()) {
            // 无有效文档：置空涵盖主题，不调用模型
            writeSummary(base, null);
            return;
        }
        if (base.ownerId() == null || base.ownerId() <= 0) {
            log.warn("知识库缺少拥有者，无法计费，跳过涵盖主题生成，knowledgeBaseId={}", base.id());
            return;
        }

        var configuration = configurationService.resolveSummary();
        var userPrompt =
                KnowledgeDocumentSummaryService.fill(
                        configuration.baseUser().content(),
                        Map.of(
                                "name", base.name() == null ? "" : base.name().strip(),
                                "description", descriptionBackground(base.description()),
                                "count", String.valueOf(documents.size()),
                                "documents", renderDocuments(documents)));

        creditGuard.precheck(base.ownerId(), STAGE, AiCreditGuard.INESTIMABLE_COST);
        var result =
                structuredOutputClient.call(
                        configuration.modelId(),
                        configuration.baseSystem().content(),
                        userPrompt,
                        KnowledgeSummaryPrompt.BASE_OUTPUT_SCHEMA_NAME,
                        KnowledgeSummaryPrompt.BASE_OUTPUT_SCHEMA,
                        BaseSummaryOutput.class);
        var summary = KnowledgeSummaryPrompt.renderBaseSummary(result.entity());
        transactionTemplate.executeWithoutResult(
                status -> {
                    settle(base, configuration.modelId(), result.response());
                    if (summary == null) {
                        // 输出无效时保留原涵盖主题，清除标记避免同一变更反复调用模型
                        log.warn("知识库涵盖主题结构化结果无效，保留原值，knowledgeBaseId={}", base.id());
                        clearStale(base);
                    } else {
                        writeSummary(base, summary);
                    }
                });
    }

    private List<DocumentInput> effectiveDocuments(Long knowledgeBaseId) {
        return jdbcTemplate.query(
                """
                SELECT title, summary
                FROM ai_knowledge_document
                WHERE knowledge_base_id = ? AND deleted = false AND active_run_id IS NOT NULL
                ORDER BY update_time DESC, id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new DocumentInput(rs.getString(1), rs.getString(2)),
                knowledgeBaseId,
                KnowledgeSummaryPrompt.BASE_MAX_DOCUMENTS);
    }

    private String descriptionBackground(String description) {
        return description == null || description.isBlank() ? "（未填写）" : description.strip();
    }

    private String renderDocuments(List<DocumentInput> documents) {
        var builder = new StringBuilder();
        for (var index = 0; index < documents.size(); index++) {
            var document = documents.get(index);
            var title = document.title() == null ? "" : document.title().strip();
            if (title.length() > MAX_TITLE_LENGTH) {
                title = title.substring(0, MAX_TITLE_LENGTH);
            }
            var summary =
                    document.summary() == null || document.summary().isBlank()
                            ? "（暂无摘要）"
                            : document.summary().strip();
            var entry = "%d. 标题：%s\n   摘要：%s\n".formatted(index + 1, title, summary);
            if (builder.length() + entry.length() > MAX_DOCUMENTS_INPUT_LENGTH) {
                break;
            }
            builder.append(entry);
        }
        return builder.toString().stripTrailing();
    }

    private void settle(StaleBase base, String modelId, ChatResponse response) {
        var metadata = response == null ? null : response.getMetadata();
        var providerUsage = metadata == null ? null : metadata.getUsage();
        long promptTokens =
                providerUsage == null || providerUsage.getPromptTokens() == null
                        ? 0
                        : providerUsage.getPromptTokens();
        long completionTokens =
                providerUsage == null || providerUsage.getCompletionTokens() == null
                        ? 0
                        : providerUsage.getCompletionTokens();
        var model = modelManagementService.getModel(modelId);
        var cost =
                price(model.getInputPricePerK(), promptTokens)
                        .add(price(model.getOutputPricePerK(), completionTokens));
        var staleAtMicros = staleAtMicros(base.staleAt());
        creditGuard.settleIdempotently(
                new AiCreditGuard.IdempotentUsageSettlement(
                        "knowledge:base-summary:%d:%d".formatted(base.id(), staleAtMicros),
                        base.tenantId(),
                        "knowledge-base-summary:" + base.id(),
                        String.valueOf(staleAtMicros),
                        0,
                        base.ownerId(),
                        model,
                        new ChatTokenUsage(promptTokens, completionTokens),
                        STAGE,
                        cost,
                        Instant.now(),
                        "知识库涵盖主题：" + base.id()));
    }

    private void writeSummary(StaleBase base, String summary) {
        var updated =
                jdbcTemplate.update(
                        """
                        UPDATE ai_knowledge_base
                        SET ai_summary = ?, ai_summary_updated_at = CURRENT_TIMESTAMP,
                            ai_summary_stale_at = NULL
                        WHERE id = ? AND ai_summary_stale_at = ?
                        """,
                        summary,
                        base.id(),
                        base.staleAt());
        if (updated != 1) {
            log.debug("知识库汇总期间有新的文档变更，保留待重算标记，knowledgeBaseId={}", base.id());
        }
    }

    private void clearStale(StaleBase base) {
        jdbcTemplate.update(
                "UPDATE ai_knowledge_base SET ai_summary_stale_at = NULL WHERE id = ? AND ai_summary_stale_at = ?",
                base.id(),
                base.staleAt());
    }

    private static long staleAtMicros(Timestamp staleAt) {
        var instant = staleAt.toInstant();
        return instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000L;
    }

    private static BigDecimal price(BigDecimal pricePerK, long tokens) {
        return (pricePerK == null ? BigDecimal.ZERO : pricePerK)
                .multiply(BigDecimal.valueOf(tokens))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }

    private record StaleBase(
            Long id,
            Timestamp staleAt,
            String name,
            String description,
            Long ownerId,
            String tenantId) {}

    private record DocumentInput(String title, String summary) {}

    private record ChatTokenUsage(long inputTokens, long outputTokens) implements AiUsage {
        @Override
        public Map<String, Object> standardUsage() {
            return Map.of("inputTokens", inputTokens, "outputTokens", outputTokens);
        }
    }
}
