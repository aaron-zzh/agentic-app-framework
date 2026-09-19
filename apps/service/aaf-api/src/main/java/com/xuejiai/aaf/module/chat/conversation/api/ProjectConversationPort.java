package com.xuejiai.aaf.module.chat.conversation.api;

import java.util.Optional;

/** 项目与 chat 聚合之间的受控会话绑定边界。 */
public interface ProjectConversationPort {

    ProjectConversation create(CreateCommand command);

    Optional<ProjectConversation> find(Long conversationRecordId);

    record CreateCommand(
            Long projectId,
            Long ownerId,
            Long orgId,
            Long workspaceId,
            Long assistantRecordId,
            String title) {}

    record ProjectConversation(
            Long conversationRecordId,
            Long projectId,
            Long ownerId,
            Long orgId,
            Long workspaceId,
            Long assistantRecordId,
            String threadId) {}
}
