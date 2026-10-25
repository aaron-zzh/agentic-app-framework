package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** 焦点块事实抽取固定输出契约。 */
public final class EntityExtractionPrompt {

    public static final String OUTPUT_CONTRACT_VERSION = "knowledge-fact-schema-v4";
    public static final int MAX_FACTS_PER_CHUNK = 100;
    public static final int MAX_RESPONSE_LENGTH = 1_000_000;
    public static final int MAX_NAME_LENGTH = 500;
    public static final int MAX_DESCRIPTION_LENGTH = 2_000;
    public static final int MAX_EVIDENCE_LENGTH = 4_000;
    public static final int MAX_ATTRIBUTES_LENGTH = 16_384;
    public static final int MAX_ATTRIBUTE_COUNT = 64;

    public static final Set<String> REQUIRED_FIELDS =
            Set.of(
                    "subject",
                    "subjectType",
                    "subjectDesc",
                    "predicate",
                    "object",
                    "objectKind",
                    "objectType",
                    "objectDesc",
                    "evidenceQuote",
                    "startOffset",
                    "endOffset",
                    "confidence");

    public static final Set<String> OPTIONAL_FIELDS = Set.of("validAt", "invalidAt", "attributes");

    public static final Set<String> RESERVED_ATTRIBUTE_KEYS =
            Set.of(
                    "assertionId",
                    "factId",
                    "evidenceId",
                    "validAt",
                    "invalidAt",
                    "recordedAt",
                    "expiredAt",
                    "expirationReason",
                    "referenceTime",
                    "knowledgeBaseId",
                    "documentId",
                    "sourceId",
                    "runId");

    public static final Set<String> ENTITY_TYPES =
            Arrays.stream(ExtractedEntityType.values())
                    .map(Enum::name)
                    .collect(Collectors.toUnmodifiableSet());

    public static final String OUTPUT_INSTRUCTIONS =
            """
            subjectType 和 ENTITY 客体的 objectType 只能使用以下大写值之一：
            PERSON、ORGANIZATION、LOCATION、EVENT、PRODUCT、TECHNOLOGY、SYSTEM、DOCUMENT、CONCEPT、METRIC、ROLE、OTHER。
            无法准确归类时必须使用 OTHER，不得创造新的类型值。

            必须先判断 objectKind，再填写 objectType 和 objectDesc：
            - 可独立识别、可作为知识图谱节点复用的对象使用 ENTITY；ENTITY 的 objectType 必须填写上述枚举值，objectDesc 填写简洁说明。
            - 数值、日期、版本号、状态、布尔值、属性文本等普通属性值使用 LITERAL；LITERAL 的 objectType 和 objectDesc 必须严格返回空字符串 ""，不得填写 OTHER、类型名称或任何说明。
            - 不得出现 objectKind="LITERAL" 但 objectType 或 objectDesc 非空的组合。

            正确示例：
            - {"object":"Spring Boot","objectKind":"ENTITY","objectType":"TECHNOLOGY","objectDesc":"Java 应用开发框架"}
            - {"object":"0.13","objectKind":"LITERAL","objectType":"","objectDesc":""}
            错误示例：
            - {"object":"0.13","objectKind":"LITERAL","objectType":"OTHER","objectDesc":"版本号"}

            objectKind 只能是 ENTITY 或 LITERAL。
            evidenceQuote 必须逐字复制 FOCUS 中一段连续原文，不得引用 CONTEXT_ONLY，不得改写、概括或规范化空白。
            startOffset 和 endOffset 是 evidenceQuote 在 FOCUS Java 字符串中的零基、左闭右开 UTF-16 字符区间；系统会按 evidenceQuote 原文重新校正偏移。
            """
                    .strip();

    private EntityExtractionPrompt() {}

    /** 将模型实体类型收敛到受控枚举，未知非空类型使用 OTHER。 */
    public static String normalizeEntityType(String value) {
        if (value == null) {
            return null;
        }
        var normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        return ENTITY_TYPES.contains(normalized) ? normalized : "OTHER";
    }
}
