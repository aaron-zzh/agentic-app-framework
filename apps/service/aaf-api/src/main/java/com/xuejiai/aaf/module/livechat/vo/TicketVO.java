package com.xuejiai.aaf.module.livechat.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.livechat.TicketPriorityEnum;
import com.xuejiai.aaf.common.enums.livechat.TicketStatusEnum;
import com.xuejiai.aaf.common.enums.livechat.TicketTypeEnum;

/** 工单 VO。 */
public record TicketVO(
        Long id,
        String ticketNo,
        String title,
        String description,
        Long userId,
        Long sessionId,
        TicketTypeEnum type,
        TicketPriorityEnum priority,
        TicketStatusEnum status,
        Long assigneeId,
        LocalDateTime slaDueTime,
        LocalDateTime closedTime,
        LocalDateTime createTime) {}
