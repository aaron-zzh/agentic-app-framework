package com.xuejiai.aaf.module.chat.message.vo;

import com.xuejiai.aaf.common.enums.chat.MessageSenderTypeEnum;
import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息分页查询参数。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "消息分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class MessagePageDTO extends PageParam {

    @Schema(description = "会话 ID")
    private Long conversationId;

    @Schema(description = "发送方类型")
    private MessageSenderTypeEnum senderType;

    @Schema(description = "内容类型")
    private MessageContentType contentType;
}
