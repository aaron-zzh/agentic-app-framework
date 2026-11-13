package com.xuejiai.aaf.module.chat.conversation.service;

import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationRolePort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.module.chat.conversation.domain.Conversation;
import com.xuejiai.aaf.module.chat.conversation.repository.ConversationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 会话当前角色（ADR-011）：读写 {@code conversation.agent_role}，助理链路的 conversationId 即 AG-UI threadId。
 *
 * <p>只决定下一轮未锁定角色时的起步角色；会话不存在或不属于当前组织时视为无会话角色。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationRoleService implements ConversationRolePort {

    /** 与 {@code conversation.agent_role} 列长度一致。 */
    private static final int MAX_ROLE_KEY_LENGTH = 64;

    private final ConversationRepository conversations;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> find(TenantId tenantId, ConversationId conversationId) {
        return conversation(tenantId, conversationId)
                .map(Conversation::getAgentRole)
                .filter(roleKey -> !roleKey.isBlank());
    }

    @Override
    @Transactional
    public void save(TenantId tenantId, ConversationId conversationId, String roleKey) {
        Objects.requireNonNull(roleKey, "roleKey 不能为空");
        var conversation = conversation(tenantId, conversationId).orElse(null);
        if (conversation == null) {
            log.debug(
                    "[会话角色] 会话不存在，跳过记录：conversationId={}，roleKey={}",
                    conversationId.value(),
                    roleKey);
            return;
        }
        if (roleKey.equals(conversation.getAgentRole())) {
            return;
        }
        if (roleKey.length() > MAX_ROLE_KEY_LENGTH) {
            log.warn(
                    "[会话角色] roleKey 超过列长度，跳过记录：conversationId={}，roleKey={}",
                    conversationId.value(),
                    roleKey);
            return;
        }
        conversation.setAgentRole(roleKey);
        conversations.save(conversation);
    }

    private Optional<Conversation> conversation(TenantId tenantId, ConversationId conversationId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        // AI 对话会话可不归属组织（org_id 为空，个人会话）；归属已由运行入口 requireOwnedAiThread 校验。
        // 有组织归属时仍必须与当前租户一致
        return conversations
                .findByThreadId(conversationId.value())
                .filter(
                        conversation ->
                                conversation.getOrgId() == null
                                        || tenantId.value()
                                                .equals(String.valueOf(conversation.getOrgId())));
    }
}
