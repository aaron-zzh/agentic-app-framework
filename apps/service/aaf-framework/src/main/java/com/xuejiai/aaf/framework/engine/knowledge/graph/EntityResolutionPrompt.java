package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.xuejiai.aaf.common.util.JsonUtils;

/** 批量实体消歧固定输出契约。可编辑系统 Prompt 存于系统参数。 */
public final class EntityResolutionPrompt {

    public static final String OUTPUT_CONTRACT_VERSION = "knowledge-entity-resolution-schema-v2";
    public static final String STRUCTURED_OUTPUT_FORMAT_VERSION = "resolution-batch-json-schema-v1";
    public static final String OUTPUT_SCHEMA_NAME = "submit_resolutions";

    /** 单次大模型消歧最多判定的 mention 数。 */
    public static final int MAX_BATCH_SIZE = 20;

    public static final int MAX_RESPONSE_LENGTH = 10_000 * MAX_BATCH_SIZE;
    public static final Set<String> ACTIONS = Set.of("LINK", "CREATE", "REVIEW");

    public static final String OUTPUT_INSTRUCTIONS =
            """
            按给定 JSON Schema 直接输出一个 JSON 对象，决策列表放在 decisions 字段中；本次不调用任何工具，上文若要求调用 submit_resolutions 工具，以本条为准。
            输入 items 中每一项包含 key、mention 和该 mention 自己的 candidates；各项相互独立，只能在本项 candidates 中选择。
            decisions 必须为每个输入 key 各返回一条，且只能包含 key、action、entityId 三个字段，不得返回解释或其他字段。
            每条决策必须严格遵守以下组合：
            - LINK：表示 mention 与本项某个候选是同一实体；entityId 必须是本项 candidates 中该候选的 UUID。
            - CREATE：表示 mention 与本项所有候选均不是同一实体；entityId 必须是 JSON null。
            - REVIEW：表示现有信息不足以可靠判断；entityId 必须是 JSON null。

            正确示例：
            {"decisions":[{"key":"m0","action":"LINK","entityId":"本项候选 UUID"},{"key":"m1","action":"CREATE","entityId":null}]}

            错误示例：
            {"key":"m1","action":"CREATE","entityId":""}
            {"key":"m0","action":"LINK","entityId":"其他项的候选 UUID"}
            {"key":"m0","action":"LINK","entityId":null}

            提交前必须自检：key 与输入一一对应；LINK 有且仅有本项候选 UUID；CREATE 和 REVIEW 有且仅有 JSON null。
            """
                    .strip();

    /** 结构化输出使用的 JSON Schema：严格约束字段与 action 枚举，entityId 可空。 */
    public static final String OUTPUT_SCHEMA = buildOutputSchema();

    private EntityResolutionPrompt() {}

    private static String buildOutputSchema() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("key", Map.of("type", "string"));
        properties.put(
                "action", Map.of("type", "string", "enum", List.of("LINK", "CREATE", "REVIEW")));
        properties.put("entityId", Map.of("type", List.of("string", "null")));

        var decision = new LinkedHashMap<String, Object>();
        decision.put("type", "object");
        decision.put("properties", properties);
        decision.put("required", List.copyOf(properties.keySet()));
        decision.put("additionalProperties", false);

        var root = new LinkedHashMap<String, Object>();
        root.put("type", "object");
        root.put("properties", Map.of("decisions", Map.of("type", "array", "items", decision)));
        root.put("required", List.of("decisions"));
        root.put("additionalProperties", false);
        return JsonUtils.toJsonString(root);
    }
}
