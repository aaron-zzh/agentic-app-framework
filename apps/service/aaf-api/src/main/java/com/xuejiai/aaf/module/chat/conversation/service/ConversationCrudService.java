package com.xuejiai.aaf.module.chat.conversation.service;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
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

    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "type",
                    "title",
                    "status",
                    "assistantId",
                    "modelId",
                    "totalTokens",
                    "staffId",
                    "priority",
                    "closedAt",
                    "createTime",
                    "updateTime");

    /** 通用 CRUD 只能创建与修改 USER 会话；PROJECT_SERVER 由项目服务独占管理。 */
    private static final String USER_MANAGEMENT_MODE = "USER";

    private final ConversationRepository conversationRepository;

    @Override
    protected ConversationRepository getRepository() {
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
        entity.setManagementMode(USER_MANAGEMENT_MODE);
        return entity;
    }

    @Override
    protected void updateEntity(Conversation entity, ConversationUpdateDTO dto) {
        if (!USER_MANAGEMENT_MODE.equals(entity.getManagementMode())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "受控项目会话只能由项目服务管理");
        }
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
                .likeIfPresent("title", query.getSearch())
                .build();
    }
}
