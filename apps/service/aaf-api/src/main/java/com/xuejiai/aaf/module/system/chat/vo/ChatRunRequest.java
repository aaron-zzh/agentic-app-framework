package com.xuejiai.aaf.module.system.chat.vo;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 统一聊天运行请求。 */
public record ChatRunRequest(
        @NotBlank String threadId,
        @NotNull List<AgUiMessage> messages,
        @NotNull @Valid ChatTarget target,
        ChatRunState state) {

    public record AgUiMessage(String role, String content) {}

    public record ChatRunState(
            Boolean persist,
            String sessionId,
            /** 用户感知上下文（pageId/operation/entityType/entityId 等，JSON 字符串） */
            String awarenessContext) {}
}
