package com.xuejiai.aaf.framework.engine.knowledge.summary;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.common.util.JsonUtils;

/**
 * 文档摘要与知识库涵盖主题的结构化输出契约。
 *
 * <p>两类输出都只描述资料实际覆盖的主题；“面向谁、回答哪类问题”属于用户填写的知识库说明（description），不由系统生成。
 */
public final class KnowledgeSummaryPrompt {

    public static final String DOCUMENT_OUTPUT_SCHEMA_NAME = "submit_document_summary";
    public static final String BASE_OUTPUT_SCHEMA_NAME = "submit_base_summary";

    /** 文档摘要输入（标题 + 章节路径 + 前几个分块正文）最大字符数。 */
    public static final int DOCUMENT_INPUT_BUDGET = 6_000;

    public static final int DOCUMENT_SUMMARY_MAX_LENGTH = 150;
    public static final int DOCUMENT_MAX_TOPICS = 6;

    /** 知识库汇总最多纳入的有效文档数。 */
    public static final int BASE_MAX_DOCUMENTS = 100;

    public static final int BASE_MAX_TOPICS = 12;
    public static final int BASE_OVERVIEW_MAX_LENGTH = 200;

    public static final int TOPIC_MAX_LENGTH = 20;

    /** 与 ai_knowledge_document.summary 列宽一致。 */
    public static final int DOCUMENT_COLUMN_MAX_LENGTH = 500;

    /** 与 ai_knowledge_base.ai_summary 列宽一致。 */
    public static final int BASE_COLUMN_MAX_LENGTH = 1_000;

    public static final String DOCUMENT_OUTPUT_SCHEMA =
            schema("summary", Map.of("type", "string"), "topics", stringArray());

    public static final String BASE_OUTPUT_SCHEMA =
            schema("topics", stringArray(), "overview", Map.of("type", "string"));

    private KnowledgeSummaryPrompt() {}

    /** 文档摘要结构化输出。 */
    public record DocumentSummaryOutput(String summary, List<String> topics) {}

    /** 知识库涵盖主题结构化输出。 */
    public record BaseSummaryOutput(List<String> topics, String overview) {}

    /**
     * 校验并渲染文档摘要，形如“概括。主题：A、B、C。”；摘要为空时返回 null。
     *
     * <p>摘要超长截断，主题去空白、去重，超长主题丢弃，最多保留 {@link #DOCUMENT_MAX_TOPICS} 个。
     */
    public static String renderDocumentSummary(DocumentSummaryOutput output) {
        if (output == null) {
            return null;
        }
        var summary = truncate(clean(output.summary()), DOCUMENT_SUMMARY_MAX_LENGTH);
        if (summary.isEmpty()) {
            return null;
        }
        var topics = topics(output.topics(), DOCUMENT_MAX_TOPICS);
        var rendered =
                topics.isEmpty()
                        ? sentence(summary)
                        : sentence(summary) + "主题：" + String.join("、", topics) + "。";
        return truncate(rendered, DOCUMENT_COLUMN_MAX_LENGTH);
    }

    /** 校验并渲染知识库涵盖主题，形如“涵盖主题：A、B、C。概述：……”；主题为空时返回 null。 */
    public static String renderBaseSummary(BaseSummaryOutput output) {
        if (output == null) {
            return null;
        }
        var topics = topics(output.topics(), BASE_MAX_TOPICS);
        if (topics.isEmpty()) {
            return null;
        }
        var overview = truncate(clean(output.overview()), BASE_OVERVIEW_MAX_LENGTH);
        var rendered = "涵盖主题：" + String.join("、", topics) + "。";
        if (!overview.isEmpty()) {
            rendered += "概述：" + sentence(overview);
        }
        return truncate(rendered, BASE_COLUMN_MAX_LENGTH);
    }

    private static List<String> topics(List<String> values, int maxCount) {
        if (values == null) {
            return List.of();
        }
        var result = new LinkedHashSet<String>();
        for (var value : values) {
            var topic = stripTrailingPunctuation(clean(value));
            if (topic.isEmpty() || topic.codePointCount(0, topic.length()) > TOPIC_MAX_LENGTH) {
                continue;
            }
            result.add(topic);
            if (result.size() >= maxCount) {
                break;
            }
        }
        return List.copyOf(result);
    }

    /** 折叠空白与换行，避免模型输出破坏提示词附录的两行展示。 */
    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip();
    }

    private static String sentence(String value) {
        return value.endsWith("。") || value.endsWith("！") || value.endsWith("？")
                ? value
                : stripTrailingPunctuation(value) + "。";
    }

    private static String stripTrailingPunctuation(String value) {
        return value.replaceAll("[。，、；：,.;:\\s]+$", "");
    }

    private static String truncate(String value, int maxCodePoints) {
        if (value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    private static Map<String, Object> stringArray() {
        var array = new LinkedHashMap<String, Object>();
        array.put("type", "array");
        array.put("items", Map.of("type", "string"));
        return array;
    }

    /** 两字段对象 schema，字段按声明顺序输出，保证 schema 文本在不同 JVM 间稳定。 */
    private static String schema(
            String firstName, Object firstSchema, String secondName, Object secondSchema) {
        var properties = new LinkedHashMap<String, Object>();
        properties.put(firstName, firstSchema);
        properties.put(secondName, secondSchema);
        var root = new LinkedHashMap<String, Object>();
        root.put("type", "object");
        root.put("properties", properties);
        root.put("required", List.of(firstName, secondName));
        root.put("additionalProperties", false);
        return JsonUtils.toJsonString(root);
    }
}
