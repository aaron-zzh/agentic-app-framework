package com.xuejiai.aaf.module.ai.role;

import java.util.List;
import java.util.UUID;

import com.xuejiai.aaf.common.util.JsonUtils;

/** ai_role.knowledge_base_ids 的 JSON UUID 数组读写。 */
final class RoleKnowledgeBaseJson {

    /** 单个角色最多绑定的知识库数量；加上助理默认库，未显式指定时最多检索 3 个。 */
    static final int MAX_BINDINGS = 2;

    private RoleKnowledgeBaseJson() {}

    /** 序列化为 JSON 字符串数组；空集合写 null，避免无意义的空数组。 */
    static String write(List<UUID> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return null;
        }
        return JsonUtils.toJsonString(
                knowledgeBaseIds.stream().distinct().map(UUID::toString).toList());
    }

    static List<UUID> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JsonUtils.parseArray(json, String.class).stream().map(UUID::fromString).toList();
        } catch (RuntimeException failure) {
            throw new IllegalStateException("ai_role.knowledge_base_ids 必须是 UUID JSON 数组", failure);
        }
    }
}
