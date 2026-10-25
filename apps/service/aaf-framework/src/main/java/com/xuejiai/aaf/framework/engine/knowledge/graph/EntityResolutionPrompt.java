package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.Set;

/** 增量实体消歧固定输出契约。可编辑系统 Prompt 存于系统参数。 */
public final class EntityResolutionPrompt {

    public static final String OUTPUT_CONTRACT_VERSION = "knowledge-entity-resolution-schema-v1";
    public static final String STRUCTURED_OUTPUT_FORMAT_VERSION = "resolution-response-entity-v2";
    public static final int MAX_RESPONSE_LENGTH = 10_000;
    public static final Set<String> ACTIONS = Set.of("LINK", "CREATE", "REVIEW");

    public static final String OUTPUT_INSTRUCTIONS =
            """
            只能返回 action 和 entityId 两个字段，不得返回解释、判定依据或其他字段。
            必须严格遵守以下组合：
            - LINK：表示 mention 与某个候选是同一实体；entityId 必须是 candidates 中该候选的 UUID，不得使用候选集合之外的值。
            - CREATE：表示 mention 与所有候选均不是同一实体；entityId 必须是 JSON null，不得返回空字符串、字符串 "null" 或任何 UUID。
            - REVIEW：表示现有信息不足以可靠判断；entityId 必须是 JSON null，不得返回空字符串、字符串 "null" 或任何 UUID。

            正确示例：
            {"action":"LINK","entityId":"候选集合中的 UUID"}
            {"action":"CREATE","entityId":null}
            {"action":"REVIEW","entityId":null}

            错误示例：
            {"action":"CREATE","entityId":""}
            {"action":"CREATE","entityId":"候选 UUID"}
            {"action":"LINK","entityId":null}

            返回前必须自检：LINK 有且仅有候选 UUID；CREATE 和 REVIEW 有且仅有 JSON null。
            """
                    .strip();

    private EntityResolutionPrompt() {}
}
