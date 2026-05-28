package com.xuejiai.aaf.module.system.chat.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 统一聊天运行请求
 *
 * @author AaronZZH & Kiro
 */
public record ChatRunRequest(
        @Schema(description = "线程 ID", example = "thread-abc123") @NotBlank String threadId,
        @Schema(description = "消息列表") @NotNull List<AgUiMessage> messages,
        @Schema(description = "聊天目标") @NotNull @Valid ChatTarget target,
        @Schema(description = "运行状态") ChatRunState state) {

    /** AG-UI 消息 */
    public record AgUiMessage(
            @Schema(description = "消息角色", example = "user") String role,
            @Schema(description = "消息内容", example = "你好") String content) {}

    /** 聊天运行状态 */
    public record ChatRunState(
            @Schema(description = "是否持久化", example = "true") Boolean persist,
            @Schema(description = "会话 ID", example = "1") String sessionId,
            @Schema(description = "用户感知上下文（JSON）", example = "{\"pageId\":\"home\"}")
                    String awarenessContext) {}
}
