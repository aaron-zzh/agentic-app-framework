package com.xuejiai.aaf.module.livechat.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 坐席发送消息 DTO。 */
public record StaffSendMessageDTO(
        @NotNull Long sessionId,
        @NotBlank String content,
        boolean internal) {}
