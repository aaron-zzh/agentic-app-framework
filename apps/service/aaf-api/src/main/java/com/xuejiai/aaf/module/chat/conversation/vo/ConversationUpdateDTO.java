package com.xuejiai.aaf.module.chat.conversation.vo;

import com.xuejiai.aaf.common.enums.chat.ConversationStatusEnum;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新会话请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "更新会话请求")
public record ConversationUpdateDTO(
        @Schema(description = "标题") String title,
        @Schema(description = "状态") ConversationStatusEnum status) {}
