package com.xuejiai.aaf.module.chat.conversation.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.chat.conversation.domain.Conversation;
import com.xuejiai.aaf.module.chat.conversation.repository.ConversationRepository;
import com.xuejiai.aaf.module.chat.conversation.vo.ConversationCreateDTO;
import com.xuejiai.aaf.module.chat.conversation.vo.ConversationPageDTO;
import com.xuejiai.aaf.module.chat.conversation.vo.ConversationUpdateDTO;
import com.xuejiai.aaf.module.chat.conversation.vo.ConversationVO;

import lombok.RequiredArgsConstructor;

/**
 * 会话 CRUD 服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationCrudService
        extends BaseCrudService<
                Conversation,
                ConversationVO,
                ConversationCreateDTO,
                ConversationUpdateDTO,
                ConversationPageDTO> {

    private final ConversationRepository conversationRepository;

    @Override
    protected JpaRepository<Conversation, Long> getRepository() {
        return conversationRepository;
    }

    @Override
    protected JpaSpecificationExecutor<Conversation> getSpecExecutor() {
        return conversationRepository;
    }

    @Override
    protected ConversationVO toVO(Conversation e) {
        return new ConversationVO(
                e.getId(),
                e.getType(),
                e.getTitle(),
                e.getStatus(),
                e.getCreatorId(),
                e.getAssistantId(),
                e.getThreadId(),
                e.getTotalTokens(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    @Override
    protected Conversation toEntity(ConversationCreateDTO dto) {
        var entity = new Conversation();
        entity.setType(dto.type());
        entity.setTitle(dto.title());
        entity.setAssistantId(dto.assistantId());
        entity.setModelId(dto.modelId());
        entity.setKnowledgeBaseId(dto.knowledgeBaseId());
        entity.setChannelExtension(dto.channelExtension());
        return entity;
    }

    @Override
    protected void updateEntity(Conversation entity, ConversationUpdateDTO dto) {
        if (dto.title() != null) entity.setTitle(dto.title());
        if (dto.status() != null) entity.setStatus(dto.status());
    }

    @Override
    protected org.springframework.data.jpa.domain.Specification<Conversation> buildSpec(
            ConversationPageDTO query) {
        return SpecificationBuilder.<Conversation>builder()
                .eqIfPresent("type", query.getType())
                .eqIfPresent("status", query.getStatus())
                .eqIfPresent("creatorId", query.getCreatorId())
                .eqIfPresent("assistantId", query.getAssistantId())
                .build();
    }

    @Override
    protected String entityName() {
        return "会话";
    }
}
