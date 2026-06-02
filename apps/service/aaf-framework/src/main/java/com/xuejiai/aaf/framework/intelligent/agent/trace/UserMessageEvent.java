package com.xuejiai.aaf.framework.intelligent.agent.trace;

/** Agent 收到用户消息事件，用于触发聊天记录持久化。 */
public record UserMessageEvent(Long userId, String conversationId, String content) {}
