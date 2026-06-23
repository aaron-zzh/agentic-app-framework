/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.tool;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuejiai.aaf.framework.agentscope.runtime.AafContextHolder;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageRequest;
import com.xuejiai.aaf.framework.messaging.MessageService;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * 发送消息工具——包装 {@link MessageService}，支持站内信 / 邮件 / 钉钉 / 企微。
 *
 * <p>发送对象默认是当前用户（{@link AafContextHolder#userId()}），也可以指定接收方。 发送渠道由 {@code channel}
 * 参数控制，站内信（INTERNAL）无需接收方 ID（自动取当前用户）。
 */
public class SendMessageTool {

    private static final Logger log = LoggerFactory.getLogger(SendMessageTool.class);

    private final MessageService messageService;

    public SendMessageTool(MessageService messageService) {
        this.messageService = messageService;
    }

    @Tool(
            description =
                    "向用户发送消息。渠道（channel）：INTERNAL=站内通知、EMAIL=邮件、DINGTALK=钉钉机器人、WECOM=企微工作通知。"
                            + "站内信不需要 recipientId（自动发给当前用户）。邮件需要 recipientId 填邮箱地址。"
                            + "生成完内容后用本工具通知用户，无需等待用户手动查收。")
    public String send_message(
            @ToolParam(name = "channel", description = "渠道：INTERNAL | EMAIL | DINGTALK | WECOM")
                    String channel,
            @ToolParam(name = "subject", description = "消息标题或摘要") String subject,
            @ToolParam(name = "content", description = "消息正文（支持 Markdown）") String content,
            @ToolParam(
                            name = "recipientId",
                            description = "接收方标识（EMAIL 填邮箱；DINGTALK/WECOM 填 userId；INTERNAL 可留空）")
                    String recipientId) {

        MessageChannel ch;
        try {
            ch = MessageChannel.valueOf(channel.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return "{\"status\":\"error\",\"message\":\"不支持的渠道: "
                    + channel
                    + "，有效值: INTERNAL/EMAIL/DINGTALK/WECOM\"}";
        }

        // INTERNAL 渠道自动取当前用户 ID
        String recipient = recipientId;
        if ((recipient == null || recipient.isBlank()) && ch == MessageChannel.INTERNAL) {
            Long userId = AafContextHolder.userId();
            if (userId == null) {
                return "{\"status\":\"error\",\"message\":\"当前上下文无 userId，无法发送站内信\"}";
            }
            recipient = String.valueOf(userId);
        }

        if (recipient == null || recipient.isBlank()) {
            return "{\"status\":\"error\",\"message\":\"channel="
                    + channel
                    + " 需要提供 recipientId\"}";
        }

        try {
            messageService.send(MessageRequest.direct(ch, subject, content, List.of(recipient)));
            log.info("[SendMessage] channel={} subject='{}' recipient={}", ch, subject, recipient);
            return "{\"status\":\"ok\",\"channel\":\""
                    + ch
                    + "\",\"recipient\":\""
                    + recipient
                    + "\"}";
        } catch (Exception e) {
            log.warn("[SendMessage] 发送失败: {}", e.getMessage());
            return "{\"status\":\"error\",\"message\":\"发送失败: " + e.getMessage() + "\"}";
        }
    }
}
