package com.xuejiai.aaf.module.chat.livechat.ticket.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 工单响应 VO。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "工单详情")
public record TicketVO(
        @Schema(description = "工单 ID") Long id,
        @Schema(description = "工单编号") String ticketNo,
        @Schema(description = "工单标题") String title,
        @Schema(description = "工单描述") String description,
        @Schema(description = "提交用户 ID") Long userId,
        @Schema(description = "关联会话 ID") Long conversationId,
        @Schema(description = "工单类型") String type,
        @Schema(description = "优先级") String priority,
        @Schema(description = "工单状态") String status,
        @Schema(description = "受理客服 ID") Long assigneeId,
        @Schema(description = "SLA 截止时间") LocalDateTime slaDueTime,
        @Schema(description = "关闭时间") LocalDateTime closedTime,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
