package com.xuejiai.aaf.module.chat.conversation.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.chat.ConversationStatusEnum;
import com.xuejiai.aaf.common.enums.chat.ConversationTypeEnum;
import com.xuejiai.aaf.framework.security.authorization.RelationPermissionWriter;
import com.xuejiai.aaf.module.chat.conversation.api.ProjectConversationPort;
import com.xuejiai.aaf.module.chat.conversation.domain.Conversation;
import com.xuejiai.aaf.module.chat.conversation.repository.ConversationRepository;

/** 仅供项目聚合调用的受控 AI 会话创建与读取服务。 */
@Service
public class ProjectConversationService implements ProjectConversationPort {

    public static final String MANAGEMENT_MODE = "PROJECT_SERVER";

    private final ConversationRepository conversations;
    private final RelationPermissionWriter relationPermissions;

    public ProjectConversationService(
            ConversationRepository conversations, RelationPermissionWriter relationPermissions) {
        this.conversations = Objects.requireNonNull(conversations, "conversations 不能为空");
        this.relationPermissions =
                Objects.requireNonNull(relationPermissions, "relationPermissions 不能为空");
    }

    @Override
    @Transactional
    public ProjectConversation create(CreateCommand command) {
        Objects.requireNonNull(command, "command 不能为空");
        requirePositive(command.projectId(), "projectId");
        requirePositive(command.ownerId(), "ownerId");
        requirePositive(command.assistantRecordId(), "assistantRecordId");
        var conversation = new Conversation();
        conversation.setType(ConversationTypeEnum.AI);
        conversation.setStatus(ConversationStatusEnum.ACTIVE);
        conversation.setManagementMode(MANAGEMENT_MODE);
        conversation.setTitle(requireText(command.title(), "title"));
        conversation.setCreatorId(command.ownerId());
        conversation.setOwnerId(command.ownerId());
        conversation.setOrgId(command.orgId());
        conversation.setWorkspaceId(command.workspaceId());
        conversation.setAssistantId(command.assistantRecordId());
        conversation.setThreadId("project:" + command.projectId() + ":" + UUID.randomUUID());
        conversations.saveAndFlush(conversation);
        relationPermissions.grant(
                command.ownerId(), "session", String.valueOf(conversation.getId()), "OWNER");
        return toProjectConversation(conversation, command.projectId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectConversation> find(Long conversationRecordId) {
        if (conversationRecordId == null || conversationRecordId < 1) {
            return Optional.empty();
        }
        return conversations
                .findById(conversationRecordId)
                .filter(conversation -> MANAGEMENT_MODE.equals(conversation.getManagementMode()))
                .filter(conversation -> conversation.getType() == ConversationTypeEnum.AI)
                .filter(conversation -> conversation.getStatus() == ConversationStatusEnum.ACTIVE)
                .map(conversation -> toProjectConversation(conversation, null));
    }

    private ProjectConversation toProjectConversation(Conversation conversation, Long projectId) {
        return new ProjectConversation(
                conversation.getId(),
                projectId,
                conversation.getCreatorId(),
                conversation.getOrgId(),
                conversation.getWorkspaceId(),
                conversation.getAssistantId(),
                conversation.getThreadId());
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(field + " 必须为正数");
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }
}
