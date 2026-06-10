package com.xuejiai.aaf.module.livechat.vo;

import java.time.LocalDateTime;

/** 工单 VO（已迁移至 chat 模块，type/priority/status 均为 String）。 */
public record TicketVO(
        Long id,
        String ticketNo,
        String title,
        String description,
        Long userId,
        Long conversationId,
        String type,
        String priority,
        String status,
        Long assigneeId,
        LocalDateTime slaDueTime,
        LocalDateTime closedTime,
        LocalDateTime createTime) {}
