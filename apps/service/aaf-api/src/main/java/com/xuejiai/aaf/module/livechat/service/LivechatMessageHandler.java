package com.xuejiai.aaf.module.livechat.service;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;
import com.xuejiai.aaf.common.enums.livechat.SenderTypeEnum;
import com.xuejiai.aaf.common.enums.livechat.SessionStatusEnum;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.service.MessageHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客服消息处理器——接入渠道消息路由。
 *
 * <p>实现 channel 模块的 MessageHandler 接口，order=100（小于 DefaultMessageHandler 的 MAX_VALUE）。
 * 接收入站消息后：
 * <ol>
 *   <li>获取或创建会话</li>
 *   <li>存储用户消息</li>
 *   <li>根据会话状态路由：BOT→智能客服回复，ACTIVE→转发给坐席（异步通知）</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LivechatMessageHandler implements MessageHandler {

    private final ChatSessionService sessionService;
    private final BotReplyService botReplyService;

    @Override
    public boolean supports(UnifiedMessage message) {
        // 处理所有文本消息（事件消息不处理）
        return message.messageType() == MessageTypeEnum.TEXT;
    }

    @Override
    public UnifiedMessage handle(UnifiedMessage message) {
        var session = sessionService.getOrCreateSession(
                message.externalUserId(), message.channelType());

        // 存储用户消息
        sessionService.saveMessage(
                session.getId(), SenderTypeEnum.USER, null, message.content(), false);

        // 根据会话状态路由
        return switch (session.getStatus()) {
            case BOT -> handleBotMode(session, message);
            case ACTIVE -> handleActiveMode(session, message);
            case WAITING -> handleWaitingMode(message);
            case CLOSED -> {
                // 已关闭的会话重新激活为 BOT 模式（getOrCreateSession 已处理）
                yield handleBotMode(session, message);
            }
        };
    }

    @Override
    public int order() {
        // 优先于 DefaultMessageHandler（Integer.MAX_VALUE）
        return 100;
    }

    /** BOT 模式：智能客服回复 */
    private UnifiedMessage handleBotMode(
            com.xuejiai.aaf.module.livechat.domain.ChatSession session, UnifiedMessage message) {
        // 检测转人工意图或敏感问题
        if (botReplyService.isSensitiveTopic(message.content())) {
            sessionService.transferToHuman(session.getId(), "complaint");
            return replyText(message, "已为您转接人工客服，请稍候...");
        }

        var reply = botReplyService.generateReply(session, message.content());
        if (reply == null) {
            // 无法回答，转人工
            sessionService.transferToHuman(session.getId(), null);
            return replyText(message, "抱歉，我暂时无法回答您的问题，正在为您转接人工客服...");
        }

        // 存储机器人回复
        sessionService.saveMessage(session.getId(), SenderTypeEnum.BOT, null, reply, false);
        return replyText(message, reply);
    }

    /** ACTIVE 模式：人工服务中，消息已存储，坐席通过工作台查看（异步推送） */
    private UnifiedMessage handleActiveMode(
            com.xuejiai.aaf.module.livechat.domain.ChatSession session, UnifiedMessage message) {
        // 消息已在上方存储，坐席通过轮询/WebSocket 获取新消息
        // 此处不回复用户（坐席手动回复）
        log.debug("人工会话消息: sessionId={}, content={}", session.getId(), message.content());
        return null;
    }

    /** WAITING 模式：等待接入中 */
    private UnifiedMessage handleWaitingMode(UnifiedMessage message) {
        return replyText(message, "正在为您排队等待人工客服，请耐心等候...");
    }

    private UnifiedMessage replyText(UnifiedMessage inbound, String content) {
        return UnifiedMessage.outboundText(
                inbound.channelType(), inbound.externalUserId(), content);
    }
}
