package com.xuejiai.aaf.module.chat.conversation.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.chat.conversation.domain.Conversation;
import com.xuejiai.aaf.module.chat.conversation.service.ConversationCrudService;
import com.xuejiai.aaf.module.chat.conversation.vo.ConversationCreateDTO;
import com.xuejiai.aaf.module.chat.conversation.vo.ConversationPageDTO;
import com.xuejiai.aaf.module.chat.conversation.vo.ConversationUpdateDTO;
import com.xuejiai.aaf.module.chat.conversation.vo.ConversationVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 会话管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "Chat - 会话管理")
@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ConversationController
        extends BaseCrudController<
                Conversation,
                ConversationVO,
                ConversationCreateDTO,
                ConversationUpdateDTO,
                ConversationPageDTO> {

    private final ConversationCrudService conversationCrudService;

    @Override
    protected ConversationCrudService getService() {
        return conversationCrudService;
    }
}
