package com.xuejiai.aaf.module.chat.livechat.ticket.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建工单请求 DTO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "创建工单请求")
public record TicketCreateDTO(
        @Schema(description = "工单标题", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "工单标题不能为空")
                String title,
        @Schema(description = "工单描述") String description,
        @Schema(description = "提交用户 ID") Long userId,
        @Schema(description = "关联会话 ID") Long conversationId,
        @Schema(description = "工单类型", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "工单类型不能为空")
                String type,
        @Schema(description = "优先级", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "优先级不能为空")
                String priority) {}
