package com.xuejiai.aaf.module.livechat.service;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.channel.MessageTypeEnum;
import com.xuejiai.aaf.module.channel.domain.UnifiedMessage;
import com.xuejiai.aaf.module.channel.service.MessageHandler;
import com.xuejiai.aaf.module.chat.conversation.domain.Conversation;
import com.xuejiai.aaf.module.chat.enums.MessageSenderType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客服消息处理器——接入渠道消息路由。
 *
 * <p>实现 channel 模块的 MessageHandler 接口，order=100（小于 DefaultMessageHandler 的 MAX_VALUE）。
 * 接收入站消息后：获取或创建会话 → 存储用户消息 → 根据状态路由（BOT/ACTIVE/WAITING/CLOSED）。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LivechatMessageHandler implements MessageHandler {

    private final ChatSessionService sessionService;
    private final BotReplyService botReplyService;

    @Override
    public boolean supports(UnifiedMessage message) {
        return message.messageType() == MessageTypeEnum.TEXT;
    }

    @Override
    public UnifiedMessage handle(UnifiedMessage message) {
        // externalUserId 转 Long（渠道侧用 openid，此处尝试解析；非数字则取 hashCode 兜底）
        Long creatorId = parseCreatorId(message.externalUserId());
        String channelType =
                message.channelType() != null ? message.channelType().name() : "UNKNOWN";

        var conversation = sessionService.getOrCreateSession(creatorId, channelType);

        // 存储用户消息
        sessionService.saveMessage(
                conversation.getId(),
                MessageSenderType.HUMAN,
                message.externalUserId(),
                message.content(),
                false);

        // 根据会话状态路由
        return switch (conversation.getStatus()) {
            case BOT -> handleBotMode(conversation, message);
            case ACTIVE -> handleActiveMode(conversation, message);
            case WAITING -> handleWaitingMode(message);
            case CLOSED -> {
                // 已关闭则重新触发一次 BOT 回复（getOrCreateSession 已新建）
                yield handleBotMode(conversation, message);
            }
            default -> null;
        };
    }

    @Override
    public int order() {
        return 100;
    }

    /** BOT 模式：智能客服回复 */
    private UnifiedMessage handleBotMode(Conversation conversation, UnifiedMessage message) {
        if (botReplyService.isSensitiveTopic(message.content())) {
            sessionService.transferToHuman(conversation.getId(), "complaint");
            return replyText(message, "已为您转接人工客服，请稍候...");
        }
        var reply = botReplyService.generateReply(conversation, message.content());
        if (reply == null) {
            sessionService.transferToHuman(conversation.getId(), null);
            return replyText(message, "抱歉，我暂时无法回答您的问题，正在为您转接人工客服...");
        }
        sessionService.saveMessage(
                conversation.getId(), MessageSenderType.BOT, "bot", reply, false);
        return replyText(message, reply);
    }

    /** ACTIVE 模式：人工服务中，消息已存储，坐席通过工作台查看 */
    private UnifiedMessage handleActiveMode(Conversation conversation, UnifiedMessage message) {
        log.debug("人工会话消息: conversationId={}, content={}", conversation.getId(), message.content());
        return null;
    }

    /** WAITING 模式：等待人工接入 */
    private UnifiedMessage handleWaitingMode(UnifiedMessage message) {
        return replyText(message, "正在为您排队等待人工客服，请耐心等候...");
    }

    private UnifiedMessage replyText(UnifiedMessage inbound, String content) {
        return UnifiedMessage.outboundText(
                inbound.channelType(), inbound.externalUserId(), content);
    }

    /** 将渠道侧 externalUserId（String）转换为 Long（openid 等非数字则取正 hashCode 兜底） */
    private Long parseCreatorId(String externalUserId) {
        if (externalUserId == null) return 0L;
        try {
            return Long.parseLong(externalUserId);
        } catch (NumberFormatException e) {
            return (long) Math.abs(externalUserId.hashCode());
        }
    }
}
