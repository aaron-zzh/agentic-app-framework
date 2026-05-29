package com.xuejiai.aaf.module.livechat.vo;

import com.xuejiai.aaf.common.enums.livechat.TicketPriorityEnum;
import com.xuejiai.aaf.common.enums.livechat.TicketTypeEnum;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 创建工单请求 DTO。 */
public record TicketCreateDTO(
        @NotBlank String title,
        String description,
        Long userId,
        Long sessionId,
        @NotNull TicketTypeEnum type,
        @NotNull TicketPriorityEnum priority) {}
