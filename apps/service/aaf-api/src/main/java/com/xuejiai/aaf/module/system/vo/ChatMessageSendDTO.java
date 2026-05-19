package com.xuejiai.aaf.module.system.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 发送聊天消息请求。 */
public record ChatMessageSendDTO(@NotNull Long sessionId, @NotBlank String content) {}
