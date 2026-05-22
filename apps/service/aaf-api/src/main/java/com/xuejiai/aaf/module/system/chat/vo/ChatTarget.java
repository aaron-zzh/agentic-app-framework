package com.xuejiai.aaf.module.system.chat.vo;

import jakarta.validation.constraints.NotBlank;

/** 聊天目标：决定消息路由到哪个处理器。 */
public record ChatTarget(
        @NotBlank String type, // ai / kiro / user
        String agentRole, // kiro 时使用
        Long userId // user 时使用
) {}
