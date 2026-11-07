package com.xuejiai.aaf.framework.engine.knowledge.summary;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.knowledge.summary.KnowledgeSummaryPrompt.DocumentSummaryOutput;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeAiMeter;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeIngestConfigurationService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.RunContext;
import com.xuejiai.aaf.framework.intelligent.ai.chat.DashScopeStructuredOutputClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文档摘要：入库代际发布成功后，根据标题、章节路径与前几个分块正文生成文档摘要，存入 {@code ai_knowledge_document.summary}。
 *
 * <p>摘要只描述文档实际覆盖的主题，供知识库涵盖主题（ai_summary）汇总使用。生成失败只记 WARN，不影响入库结果；完成后标记知识库涵盖主题待重算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentSummaryService {

    static final String STAGE = "knowledge-document-summary";
    static final String UNIT_KEY = "document-summary";

    private static final int MAX_CHUNKS = 30;
    private static final int MAX_SECTION_PATHS = 30;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)\\}");

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeIngestConfigurationService configurationService;
    private final DashScopeStructuredOutputClient structuredOutputClient;
    private final KnowledgeAiMeter meter;
    private final TrustedKnowledgeStore store;

    /**
     * 为已发布代际生成文档摘要；本方法不抛出异常。
     *
     * @param fingerprintHit 本次入库是否命中已有代际指纹（内容与配置未变），命中且已有摘要时跳过
     */
    public void summarize(RunContext run, boolean fingerprintHit) {
        try {
            if (fingerprintHit && hasSummary(run)) {
                return;
            }
            var summary = generate(run);
            saveSummary(run, summary);
        } catch (RuntimeException failure) {
            log.warn(
                    "知识文档摘要生成失败，不影响入库，documentId={}, runId={}, 原因={}",
                    run.documentId(),
                    run.runId(),
                    failure.getMessage());
            clearSummary(run);
        }
        markBaseStale(run);
    }

    private String generate(RunContext run) {
        var title =
                jdbcTemplate.queryForObject(
                        "SELECT title FROM ai_knowledge_document WHERE id = ?",
                        String.class,
                        run.documentId());
        var chunks =
                jdbcTemplate.query(
                        """
                        SELECT section_path, content
                        FROM ai_knowledge_chunk
                        WHERE run_id = ? AND document_id = ? AND enabled
                        ORDER BY chunk_index
                        LIMIT ?
                        """,
                        (rs, rowNum) -> new ChunkInput(rs.getString(1), rs.getString(2)),
                        run.runId(),
                        run.documentId(),
                        MAX_CHUNKS);
        var input = buildInput(title, chunks);
        var configuration = configurationService.resolveSummary();
        var userPrompt =
                fill(
                        configuration.documentUser().content(),
                        Map.of(
                                "title", input.title(),
                                "sections", input.sections(),
                                "truncated", String.valueOf(input.truncated()),
                                "content", input.content()));
        var systemPrompt = configuration.documentSystem().content();
        var meteredInput =
                String.join(
                        "|",
                        configuration.documentSystem().sha256(),
                        configuration.documentUser().sha256(),
                        KnowledgeSummaryPrompt.DOCUMENT_OUTPUT_SCHEMA_NAME,
                        userPrompt);
        var output =
                meter.invokeDocumentSummary(
                        run.billing(),
                        STAGE,
                        UNIT_KEY,
                        configuration.modelId(),
                        meteredInput,
                        () -> {
                            var result =
                                    structuredOutputClient.call(
                                            configuration.modelId(),
                                            systemPrompt,
                                            userPrompt,
                                            KnowledgeSummaryPrompt.DOCUMENT_OUTPUT_SCHEMA_NAME,
                                            KnowledgeSummaryPrompt.DOCUMENT_OUTPUT_SCHEMA,
                                            DocumentSummaryOutput.class);
                            return new ResponseEntity<>(result.response(), result.entity());
                        });
        var summary = KnowledgeSummaryPrompt.renderDocumentSummary(output);
        if (summary == null) {
            throw new IllegalStateException("文档摘要结构化结果缺少 summary");
        }
        return summary;
    }

    /** 标题 + 章节路径 + 前几个分块正文，合计不超过 {@link KnowledgeSummaryPrompt#DOCUMENT_INPUT_BUDGET} 字符。 */
    private SummaryInput buildInput(String title, List<ChunkInput> chunks) {
        var budget = KnowledgeSummaryPrompt.DOCUMENT_INPUT_BUDGET;
        var safeTitle = truncate(title == null ? "" : title.strip(), 200);
        budget -= safeTitle.length();

        var sectionPaths = new LinkedHashSet<String>();
        for (var chunk : chunks) {
            if (chunk.sectionPath() != null && !chunk.sectionPath().isBlank()) {
                sectionPaths.add(chunk.sectionPath().strip());
            }
            if (sectionPaths.size() >= MAX_SECTION_PATHS) {
                break;
            }
        }
        var sections = truncate(String.join("\n", sectionPaths), Math.min(budget / 4, 1_000));
        budget -= sections.length();

        var content = new StringBuilder();
        var truncated = false;
        for (var chunk : chunks) {
            var text = chunk.content() == null ? "" : chunk.content().strip();
            if (text.isEmpty()) {
                continue;
            }
            var separator = content.isEmpty() ? 0 : 2;
            var remaining = budget - content.length() - separator;
            if (remaining <= 0) {
                truncated = true;
                break;
            }
            if (separator > 0) {
                content.append("\n\n");
            }
            if (text.length() > remaining) {
                content.append(text, 0, remaining);
                truncated = true;
                break;
            }
            content.append(text);
        }
        if (chunks.size() >= MAX_CHUNKS) {
            truncated = true;
        }
        if (content.isEmpty()) {
            throw new IllegalStateException("文档没有可用于摘要的启用分块");
        }
        return new SummaryInput(safeTitle, sections, content.toString(), truncated);
    }

    private boolean hasSummary(RunContext run) {
        var summary =
                jdbcTemplate.queryForObject(
                        "SELECT summary FROM ai_knowledge_document WHERE id = ?",
                        String.class,
                        run.documentId());
        return summary != null && !summary.isBlank();
    }

    /** 仅当文档当前代际仍是本代际时写入，避免迟到结果覆盖新代际的摘要。 */
    private void saveSummary(RunContext run, String summary) {
        jdbcTemplate.update(
                "UPDATE ai_knowledge_document SET summary = ? WHERE id = ? AND active_run_id = ?",
                summary,
                run.documentId(),
                run.runId());
    }

    /** 新代际摘要失败时清除旧摘要，避免旧内容的摘要参与知识库涵盖主题汇总。 */
    private void clearSummary(RunContext run) {
        try {
            jdbcTemplate.update(
                    "UPDATE ai_knowledge_document SET summary = NULL WHERE id = ? AND active_run_id = ?",
                    run.documentId(),
                    run.runId());
        } catch (RuntimeException failure) {
            log.warn("清除知识文档旧摘要失败，documentId={}, 原因={}", run.documentId(), failure.getMessage());
        }
    }

    private void markBaseStale(RunContext run) {
        try {
            store.markSummaryStale(run.knowledgeBaseId());
        } catch (RuntimeException failure) {
            log.warn(
                    "标记知识库涵盖主题待重算失败，knowledgeBaseId={}, 原因={}",
                    run.knowledgeBaseId(),
                    failure.getMessage());
        }
    }

    /** 单遍替换模板占位符，数据中出现的花括号文本不会被再次替换。 */
    static String fill(String template, Map<String, String> values) {
        var matcher = PLACEHOLDER.matcher(template);
        var result = new StringBuilder();
        while (matcher.find()) {
            var value = values.get(matcher.group(1));
            matcher.appendReplacement(
                    result, Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String truncate(String value, int maxLength) {
        if (maxLength <= 0) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record ChunkInput(String sectionPath, String content) {}

    private record SummaryInput(String title, String sections, String content, boolean truncated) {}
}
