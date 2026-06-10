package com.xuejiai.aaf.module.chat.message.vo;

import com.xuejiai.aaf.module.chat.enums.MessageContentType;
import com.xuejiai.aaf.module.chat.enums.MessageSenderType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建消息请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建消息请求")
public record MessageCreateDTO(
        @Schema(description = "会话 ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                Long conversationId,
        @Schema(description = "发送方 ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String senderId,
        @Schema(description = "发送方类型", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                MessageSenderType senderType,
        @Schema(description = "LLM 角色（user/assistant/system/tool）") String role,
        @Schema(description = "消息内容") String content,
        @Schema(description = "内容类型") MessageContentType contentType,
        @Schema(description = "结构化载荷 JSON") String payload,
        @Schema(description = "引用消息 ID") Long replyToId,
        @Schema(description = "是否内部可见") Boolean isInternal,
        @Schema(description = "感知上下文快照 JSON") String awarenessContext) {}
