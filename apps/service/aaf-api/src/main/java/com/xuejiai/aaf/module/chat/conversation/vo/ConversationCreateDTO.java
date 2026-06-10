package com.xuejiai.aaf.module.chat.conversation.vo;

import com.xuejiai.aaf.module.chat.enums.ConversationType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 创建会话请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建会话请求")
public record ConversationCreateDTO(
        @Schema(description = "会话类型", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                ConversationType type,
        @Schema(description = "标题") String title,
        @Schema(description = "助理 ID（type=AI 时）") Long assistantId,
        @Schema(description = "模型 ID") String modelId,
        @Schema(description = "知识库 ID") Long knowledgeBaseId,
        @Schema(description = "渠道扩展信息 JSON（type=LIVECHAT 时）") String channelExtension) {}
