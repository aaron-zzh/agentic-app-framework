package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.util.Objects;

/** Agent 端口使用的文本消息，不暴露运行时消息类型。 */
public record AgentMessage(String messageId, Role role, String text) {

    public AgentMessage {
        Objects.requireNonNull(messageId, "messageId 不能为空");
        Objects.requireNonNull(role, "role 不能为空");
        Objects.requireNonNull(text, "text 不能为空");
        if (messageId.isBlank()) {
            throw new IllegalArgumentException("messageId 不能为空白");
        }
    }

    /** 消息作者角色。 */
    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM,
        TOOL
    }
}
