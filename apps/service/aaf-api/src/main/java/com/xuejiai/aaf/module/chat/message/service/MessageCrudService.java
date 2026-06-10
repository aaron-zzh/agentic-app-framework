package com.xuejiai.aaf.module.chat.message.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.chat.message.domain.ConversationMessage;
import com.xuejiai.aaf.module.chat.message.repository.ConversationMessageRepository;
import com.xuejiai.aaf.module.chat.message.vo.MessageCreateDTO;
import com.xuejiai.aaf.module.chat.message.vo.MessagePageDTO;
import com.xuejiai.aaf.module.chat.message.vo.MessageUpdateDTO;
import com.xuejiai.aaf.module.chat.message.vo.MessageVO;

import lombok.RequiredArgsConstructor;

/**
 * 消息 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageCrudService
        extends BaseCrudService<
                ConversationMessage,
                MessageVO,
                MessageCreateDTO,
                MessageUpdateDTO,
                MessagePageDTO> {

    private final ConversationMessageRepository messageRepository;

    @Override
    protected JpaRepository<ConversationMessage, Long> getRepository() {
        return messageRepository;
    }

    @Override
    protected JpaSpecificationExecutor<ConversationMessage> getSpecExecutor() {
        return messageRepository;
    }

    @Override
    protected MessageVO toVO(ConversationMessage e) {
        return new MessageVO(
                e.getId(),
                e.getConversationId(),
                e.getSenderId(),
                e.getSenderType(),
                e.getRole(),
                e.getContent(),
                e.getContentType(),
                e.getIsInternal(),
                e.getTokenCount(),
                e.getCreateTime());
    }

    @Override
    protected ConversationMessage toEntity(MessageCreateDTO dto) {
        var entity = new ConversationMessage();
        entity.setConversationId(dto.conversationId());
        entity.setSenderId(dto.senderId());
        entity.setSenderType(dto.senderType());
        if (dto.role() != null) entity.setRole(dto.role());
        entity.setContent(dto.content());
        if (dto.contentType() != null) entity.setContentType(dto.contentType());
        entity.setPayload(dto.payload());
        entity.setReplyToId(dto.replyToId());
        if (dto.isInternal() != null) entity.setIsInternal(dto.isInternal());
        entity.setAwarenessContext(dto.awarenessContext());
        return entity;
    }

    @Override
    protected void updateEntity(ConversationMessage entity, MessageUpdateDTO dto) {
        if (dto.content() != null) entity.setContent(dto.content());
    }

    @Override
    protected org.springframework.data.jpa.domain.Specification<ConversationMessage> buildSpec(
            MessagePageDTO query) {
        return SpecificationBuilder.<ConversationMessage>builder()
                .eqIfPresent("conversationId", query.getConversationId())
                .eqIfPresent("senderType", query.getSenderType())
                .eqIfPresent("contentType", query.getContentType())
                .build();
    }

    @Override
    protected String entityName() {
        return "消息";
    }
}
