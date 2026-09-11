package com.xuejiai.aaf.module.ai.chat.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.chat.ConversationStatusEnum;
import com.xuejiai.aaf.common.enums.chat.ConversationTypeEnum;
import com.xuejiai.aaf.common.enums.chat.MessageSenderTypeEnum;
import com.xuejiai.aaf.common.enums.chat.ParticipantRoleEnum;
import com.xuejiai.aaf.common.enums.chat.ParticipantTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.assistant.SystemAssistantTemplateIds;
import com.xuejiai.aaf.module.channel.service.WebCustomerServiceBindingResolver.Binding;
import com.xuejiai.aaf.module.chat.conversation.domain.Conversation;
import com.xuejiai.aaf.module.chat.conversation.domain.ConversationParticipant;
import com.xuejiai.aaf.module.chat.conversation.repository.ConversationParticipantRepository;
import com.xuejiai.aaf.module.chat.conversation.repository.ConversationRepository;
import com.xuejiai.aaf.module.chat.message.domain.ConversationMessage;
import com.xuejiai.aaf.module.chat.message.repository.ConversationMessageRepository;
import com.xuejiai.aaf.module.system.ErrorCodeConstants;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** 匿名客服持久会话与消息的唯一业务入口。 */
@Service
@RequiredArgsConstructor
public class CustomerServiceConversationService {

    private static final List<ConversationStatusEnum> HISTORY_STATUSES =
            List.of(ConversationStatusEnum.ACTIVE, ConversationStatusEnum.ARCHIVED);
    private static final List<ConversationStatusEnum> RUN_STATUSES =
            List.of(ConversationStatusEnum.ACTIVE);

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final ConversationMessageRepository messageRepository;
    private final EntityManager entityManager;

    @Transactional
    public Session resolveOrCreate(String visitorSubject, Binding binding) {
        requireVisitorSubject(visitorSubject);
        lockVisitor(visitorSubject, binding.orgId());

        var existing =
                conversationRepository.findVisitorConversations(
                        binding.orgId(),
                        ConversationTypeEnum.LIVECHAT,
                        HISTORY_STATUSES,
                        visitorSubject,
                        ParticipantTypeEnum.VISITOR);
        if (existing.size() > 1) {
            throw new IllegalStateException("访客存在多个可恢复客服会话");
        }
        if (!existing.isEmpty()) {
            var conversation = existing.getFirst();
            requireBindingSnapshot(conversation, binding);
            if (conversation.getStatus() == ConversationStatusEnum.ARCHIVED) {
                conversation.setStatus(ConversationStatusEnum.ACTIVE);
                conversationRepository.save(conversation);
            }
            return new Session(conversation.getThreadId());
        }
        return create(visitorSubject, binding);
    }

    @Transactional(readOnly = true)
    public List<PublicMessage> listMessages(
            String visitorSubject, String threadId, Binding binding) {
        var conversation = requireHistory(visitorSubject, threadId, binding);
        return messageRepository
                .findByConversationIdAndIsInternalFalseOrderByCreateTimeAsc(conversation.getId())
                .stream()
                .map(
                        message ->
                                new PublicMessage(
                                        message.getId(),
                                        message.getSenderId(),
                                        message.getSenderType(),
                                        message.getRole(),
                                        message.getContent(),
                                        message.getCreateTime()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Conversation requireActive(String visitorSubject, String threadId, Binding binding) {
        return require(visitorSubject, threadId, binding, RUN_STATUSES);
    }

    @Transactional
    public void saveVisitorMessage(
            String visitorSubject, String threadId, Binding binding, String content) {
        var conversation = requireActive(visitorSubject, threadId, binding);
        saveMessage(
                conversation,
                visitorSubject,
                MessageSenderTypeEnum.HUMAN,
                "user",
                requireContent(content),
                null);
    }

    @Transactional
    public void saveAssistantMessage(
            String visitorSubject,
            String threadId,
            Binding binding,
            String content,
            String externalMessageId) {
        var conversation = requireActive(visitorSubject, threadId, binding);
        saveMessage(
                conversation,
                ChatService.NON_HUMAN_SENDER_ID,
                MessageSenderTypeEnum.ASSISTANT,
                "assistant",
                requireContent(content),
                externalMessageId);
    }

    private Session create(String visitorSubject, Binding binding) {
        var threadId = UUID.randomUUID().toString();
        var extension = JsonUtils.createObjectNode();
        extension.put("platformId", binding.platformId());
        extension.put("bindingId", binding.bindingId());
        extension.put("assistantId", binding.assistantId());

        var conversation = new Conversation();
        conversation.setOrgId(binding.orgId());
        conversation.setOwnerId(binding.responsibleOwnerId());
        conversation.setCreatorId(null);
        conversation.setType(ConversationTypeEnum.LIVECHAT);
        conversation.setStatus(ConversationStatusEnum.ACTIVE);
        conversation.setTitle("匿名客服");
        conversation.setThreadId(threadId);
        conversation.setChannelExtension(extension.toString());
        conversationRepository.saveAndFlush(conversation);

        var visitor = new ConversationParticipant();
        visitor.setConversationId(conversation.getId());
        visitor.setParticipantId(visitorSubject);
        visitor.setParticipantType(ParticipantTypeEnum.VISITOR);
        visitor.setRole(ParticipantRoleEnum.OWNER);

        var assistant = new ConversationParticipant();
        assistant.setConversationId(conversation.getId());
        assistant.setParticipantId(SystemAssistantTemplateIds.CUSTOMER_SERVICE);
        assistant.setParticipantType(ParticipantTypeEnum.ASSISTANT);
        assistant.setRole(ParticipantRoleEnum.MEMBER);
        participantRepository.saveAll(List.of(visitor, assistant));
        return new Session(threadId);
    }

    private Conversation requireHistory(String visitorSubject, String threadId, Binding binding) {
        return require(visitorSubject, threadId, binding, HISTORY_STATUSES);
    }

    private Conversation require(
            String visitorSubject,
            String threadId,
            Binding binding,
            List<ConversationStatusEnum> statuses) {
        requireVisitorSubject(visitorSubject);
        var matches =
                conversationRepository.findVisitorThread(
                        requireThreadId(threadId),
                        binding.orgId(),
                        ConversationTypeEnum.LIVECHAT,
                        statuses,
                        visitorSubject,
                        ParticipantTypeEnum.VISITOR);
        if (matches.size() != 1) {
            throw notFound();
        }
        var conversation = matches.getFirst();
        requireBindingSnapshot(conversation, binding);
        return conversation;
    }

    private void requireBindingSnapshot(Conversation conversation, Binding binding) {
        try {
            var snapshot = JsonUtils.readTreeStrict(conversation.getChannelExtension());
            if (snapshot == null
                    || snapshot.path("platformId").longValue() != binding.platformId()
                    || snapshot.path("bindingId").longValue() != binding.bindingId()
                    || !binding.assistantId().equals(snapshot.path("assistantId").asString())) {
                throw notFound();
            }
        } catch (BusinessException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw notFound();
        }
    }

    private void saveMessage(
            Conversation conversation,
            String senderId,
            MessageSenderTypeEnum senderType,
            String role,
            String content,
            String externalMessageId) {
        var message = new ConversationMessage();
        message.setOrgId(conversation.getOrgId());
        message.setOwnerId(conversation.getOwnerId());
        message.setConversationId(conversation.getId());
        message.setSenderId(senderId);
        message.setSenderType(senderType);
        message.setRole(role);
        message.setContent(content);
        if (externalMessageId != null && !externalMessageId.isBlank()) {
            var payload = JsonUtils.createObjectNode();
            payload.put("aguiMessageId", externalMessageId);
            message.setPayload(payload.toString());
        }
        messageRepository.save(message);
    }

    private void lockVisitor(String visitorSubject, Long orgId) {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?1, ?2))")
                .setParameter(1, visitorSubject)
                .setParameter(2, orgId)
                .getSingleResult();
    }

    private static void requireVisitorSubject(String visitorSubject) {
        if (visitorSubject == null || visitorSubject.isBlank() || visitorSubject.length() > 64) {
            throw notFound();
        }
    }

    private static String requireThreadId(String threadId) {
        if (threadId == null || threadId.isBlank() || threadId.length() > 64) {
            throw notFound();
        }
        return threadId.trim();
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("客服消息内容不能为空白");
        }
        return content;
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCodeConstants.CHAT_SESSION_NOT_FOUND);
    }

    public record Session(String threadId) {}

    public record PublicMessage(
            Long id,
            String senderId,
            MessageSenderTypeEnum senderType,
            String role,
            String content,
            LocalDateTime createdAt) {}
}
