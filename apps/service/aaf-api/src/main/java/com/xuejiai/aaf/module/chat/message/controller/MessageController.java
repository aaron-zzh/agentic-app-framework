package com.xuejiai.aaf.module.chat.message.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.chat.message.domain.ConversationMessage;
import com.xuejiai.aaf.module.chat.message.service.MessageCrudService;
import com.xuejiai.aaf.module.chat.message.vo.MessageCreateDTO;
import com.xuejiai.aaf.module.chat.message.vo.MessagePageDTO;
import com.xuejiai.aaf.module.chat.message.vo.MessageUpdateDTO;
import com.xuejiai.aaf.module.chat.message.vo.MessageVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 消息管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "Chat - 消息管理")
@RestController
@RequestMapping("/api/chat/messages")
@RequiredArgsConstructor
public class MessageController
        extends BaseCrudController<
                ConversationMessage,
                MessageVO,
                MessageCreateDTO,
                MessageUpdateDTO,
                MessagePageDTO> {

    private final MessageCrudService messageCrudService;

    @Override
    protected MessageCrudService getService() {
        return messageCrudService;
    }
}
