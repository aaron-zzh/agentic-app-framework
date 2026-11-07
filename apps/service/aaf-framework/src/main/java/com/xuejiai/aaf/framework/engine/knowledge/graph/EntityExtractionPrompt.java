package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.xuejiai.aaf.common.util.JsonUtils;

/** 焦点块事实抽取固定输出契约。 */
public final class EntityExtractionPrompt {

    public static final String OUTPUT_CONTRACT_VERSION = "knowledge-fact-schema-v6";
    public static final String OUTPUT_SCHEMA_NAME = "submit_facts";
    public static final int MAX_FACTS_PER_CHUNK = 100;
    public static final int MAX_RESPONSE_LENGTH = 1_000_000;
    public static final int MAX_NAME_LENGTH = 500;
    public static final int MAX_DESCRIPTION_LENGTH = 2_000;
    public static final int MAX_EVIDENCE_LENGTH = 4_000;
    public static final int MAX_ATTRIBUTES_LENGTH = 16_384;
    public static final int MAX_ATTRIBUTE_COUNT = 64;

    /** LITERAL 客体去首尾空白后的最大字符数。 */
    public static final int MAX_LITERAL_LENGTH = 32;

    /** LITERAL 客体不得包含的句子分隔字符；半角句点和逗号不计入，避免误伤 7.0、1,000。 */
    public static final String LITERAL_SENTENCE_DELIMITERS = "。！？；，!?;\n\r";

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
            按给定 JSON Schema 直接输出一个 JSON 对象，事实列表放在 facts 字段中，没有可验证事实时输出空 facts 数组；本次不调用任何工具，上文若要求调用 submit_facts，以本条为准。
            subjectType 和 ENTITY 客体的 objectType 只能使用以下大写值之一：
            PERSON、ORGANIZATION、LOCATION、EVENT、PRODUCT、TECHNOLOGY、SYSTEM、DOCUMENT、CONCEPT、METRIC、ROLE、OTHER。
            无法准确归类时必须使用 OTHER，不得创造新的类型值。

            必须先判断 objectKind，再填写 objectType 和 objectDesc：
            - objectKind 是二选一字段，值只能是 ENTITY 或 LITERAL；CONCEPT、PRODUCT 等类型名只能写在 subjectType 或 objectType 中，绝不能写进 objectKind。
            - 可独立识别、可作为知识图谱节点复用的对象使用 ENTITY；ENTITY 的 objectType 必须填写上述枚举值，objectDesc 填写简洁说明。
            - 数值（可带单位）、日期或时间、版本号、状态值、布尔值、短属性值使用 LITERAL；LITERAL 的 objectType 和 objectDesc 必须严格返回空字符串 ""，不得填写 OTHER、类型名称或任何说明。
            - LITERAL 的 object 最长 32 个字符，且不得是句子：不得包含。！？；，!?; 或换行。整句描述必须拆成实体加关系表达；无法拆分时不抽取该事实。超长或含句子标点的 LITERAL 会被系统直接丢弃。
            - 不得出现 objectKind="LITERAL" 但 objectType 或 objectDesc 非空的组合。

            正确示例：
            - {"object":"Spring Boot","objectKind":"ENTITY","objectType":"TECHNOLOGY","objectDesc":"Java 应用开发框架"}
            - {"object":"视网膜病变","objectKind":"ENTITY","objectType":"CONCEPT","objectDesc":"糖尿病并发症之一"}
            - {"object":"0.13","objectKind":"LITERAL","objectType":"","objectDesc":""}
            - {"object":"7.0 mmol/L","objectKind":"LITERAL","objectType":"","objectDesc":""}
            错误示例：
            - {"object":"视网膜病变","objectKind":"CONCEPT","objectType":"CONCEPT","objectDesc":"糖尿病并发症之一"}（objectKind 填了类型名，应为 ENTITY）
            - {"object":"0.13","objectKind":"LITERAL","objectType":"OTHER","objectDesc":"版本号"}（LITERAL 的 objectType 和 objectDesc 必须为空字符串）
            - {"object":"患者应在医生指导下长期坚持低糖饮食并定期复查。","objectKind":"LITERAL","objectType":"","objectDesc":""}（整句不能作为 LITERAL，应拆为"低糖饮食"等实体加关系，无法拆分则不抽取）

            evidenceQuote 规则：
            - 必须从 FOCUS 中逐字复制一段连续原文，不得引用 CONTEXT_ONLY，不得改写、概括、补字、删字或拼接不相邻的片段。
            - 原文中的 Markdown 标记（如 **、#、-、>、`）、空格（含中文与数字之间的空格）、全角或半角标点、换行都必须原样保留，不得删除或替换。
            - 优先引用能支撑该事实的最短完整句或分句；无法确定原文准确写法时，改为引用更短、能确保逐字一致的连续片段。
            - 系统会用 evidenceQuote 在 FOCUS 中做精确匹配，原文找不到的事实会被直接丢弃。
            startOffset 和 endOffset 是 evidenceQuote 在 FOCUS Java 字符串中的零基、左闭右开 UTF-16 字符区间；系统会按 evidenceQuote 原文重新校正偏移，因此 evidenceQuote 的逐字准确性优先于偏移数值。

            validAt 和 invalidAt 只能是 ISO-8601 UTC 时间（如 2025-12-31T00:00:00Z）或 null；原文时间无法确定到具体时刻时填 null，不得填写“年底”“前”等中文描述或空字符串。
            attributes 可省略；需要时只放 FOCUS 中明确出现的补充属性。

            提交前逐条自检：objectKind 是否只为 ENTITY 或 LITERAL；LITERAL 是否为不超过 32 字符的短值而非句子；evidenceQuote 是否能在 FOCUS 中原样找到。
            """
                    .strip();

    /** 结构化输出使用的 JSON Schema：严格约束字段、枚举与可空时间，attributes 为可选自由对象。 */
    public static final String OUTPUT_SCHEMA = buildOutputSchema();

    private EntityExtractionPrompt() {}

    private static String buildOutputSchema() {
        var entityTypes = Arrays.stream(ExtractedEntityType.values()).map(Enum::name).toList();
        var objectTypes = new ArrayList<>(entityTypes);
        objectTypes.add("");
        var string = Map.of("type", "string");
        var nullableTime = Map.of("type", List.of("string", "null"), "format", "date-time");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("subject", string);
        properties.put("subjectType", Map.of("type", "string", "enum", entityTypes));
        properties.put("subjectDesc", string);
        properties.put("predicate", string);
        properties.put("object", string);
        properties.put(
                "objectKind", Map.of("type", "string", "enum", List.of("ENTITY", "LITERAL")));
        properties.put("objectType", Map.of("type", "string", "enum", objectTypes));
        properties.put("objectDesc", string);
        properties.put("evidenceQuote", string);
        properties.put("startOffset", Map.of("type", "integer"));
        properties.put("endOffset", Map.of("type", "integer"));
        properties.put("confidence", Map.of("type", "number"));
        properties.put("validAt", nullableTime);
        properties.put("invalidAt", nullableTime);
        properties.put("attributes", Map.of("type", "object"));

        var required = new ArrayList<>(properties.keySet());
        required.remove("attributes");
        var fact = new LinkedHashMap<String, Object>();
        fact.put("type", "object");
        fact.put("properties", properties);
        fact.put("required", required);
        fact.put("additionalProperties", false);

        var root = new LinkedHashMap<String, Object>();
        root.put("type", "object");
        root.put("properties", Map.of("facts", Map.of("type", "array", "items", fact)));
        root.put("required", List.of("facts"));
        root.put("additionalProperties", false);
        return JsonUtils.toJsonString(root);
    }

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
