package com.xuejiai.aaf.module.chat.livechat.ticket.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新工单请求 DTO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "更新工单请求")
public record TicketUpdateDTO(
        @Schema(description = "工单标题") String title,
        @Schema(description = "工单描述") String description,
        @Schema(description = "受理客服 ID") Long assigneeId,
        @Schema(description = "优先级") String priority,
        @Schema(description = "SLA 截止时间") LocalDateTime slaDueTime) {}
