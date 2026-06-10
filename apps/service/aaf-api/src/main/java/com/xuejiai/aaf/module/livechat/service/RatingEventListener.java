package com.xuejiai.aaf.module.livechat.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 评价事件监听器。
 *
 * <p>监听会话关闭事件，触发评价邀请。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RatingEventListener {

    /**
     * 会话关闭后触发评价邀请。
     *
     * <p>当前实现为日志记录，后续可对接消息推送（评价卡片）。 externalUserId 存储在 channelExtension JSON 中，此处用 creatorId 标识访客。
     */
    @EventListener
    public void onSessionClosed(SessionClosedEvent event) {
        var conversation = event.conversation();
        // 仅人工服务过的会话才邀请评价
        if (conversation.getStaffId() == null) {
            return;
        }
        log.info(
                "评价邀请: conversationId={}, creatorId={}, staffId={}",
                conversation.getId(),
                conversation.getCreatorId(),
                conversation.getStaffId());
        // 后续可通过 ChannelMessageRouter 推送评价卡片给用户
    }
}
