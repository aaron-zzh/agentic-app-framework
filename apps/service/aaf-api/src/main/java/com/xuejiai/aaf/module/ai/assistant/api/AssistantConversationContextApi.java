package com.xuejiai.aaf.module.ai.assistant.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Assistant 模块对实时会话等业务模块暴露的冻结上下文接口。 */
public interface AssistantConversationContextApi {

    /** 使用默认 Assistant 与指定或默认 Role 解析会话上下文。 */
    ConversationContext resolve(ConversationIdentity identity, String roleKey);

    /** 会话可信身份；operatorUserId 预留给未来无用户操作员的设备会话。 */
    record ConversationIdentity(
            Long operatorUserId, Long ownerUserId, Long orgId, Long workspaceId) {

        public ConversationIdentity {
            Objects.requireNonNull(ownerUserId, "ownerUserId 不能为空");
            Objects.requireNonNull(orgId, "orgId 不能为空");
            Objects.requireNonNull(workspaceId, "workspaceId 不能为空");
        }
    }

    /** 已解析且可冻结到单个会话的 Assistant 上下文。 */
    record ConversationContext(
            String assistantId,
            long assistantRevision,
            String roleKey,
            String instructions,
            Set<UUID> knowledgeBaseIds) {

        public ConversationContext {
            assistantId = requireText(assistantId, "assistantId");
            if (assistantRevision < 0) {
                throw new IllegalArgumentException("assistantRevision 不能小于 0");
            }
            roleKey = requireText(roleKey, "roleKey");
            instructions = requireText(instructions, "instructions");
            knowledgeBaseIds =
                    Collections.unmodifiableSet(
                            new LinkedHashSet<>(
                                    Objects.requireNonNull(
                                            knowledgeBaseIds, "knowledgeBaseIds 不能为空")));
        }

        private static String requireText(String value, String field) {
            Objects.requireNonNull(value, field + " 不能为空");
            if (value.isBlank()) {
                throw new IllegalArgumentException(field + " 不能为空白");
            }
            return value;
        }
    }
}
