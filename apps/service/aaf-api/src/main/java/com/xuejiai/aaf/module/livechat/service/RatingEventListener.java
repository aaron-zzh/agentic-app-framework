package com.xuejiai.aaf.module.livechat.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 评价事件监听器。
 *
 * <p>监听会话关闭事件，触发评价邀请。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RatingEventListener {

    /**
     * 会话关闭后触发评价邀请。
     *
     * <p>当前实现为日志记录，后续可对接消息推送（评价卡片）。
     */
    @EventListener
    public void onSessionClosed(SessionClosedEvent event) {
        var session = event.session();
        // 仅人工服务过的会话才邀请评价
        if (session.getStaffId() == null) {
            return;
        }
        log.info("评价邀请: sessionId={}, userId={}, staffId={}",
                session.getId(), session.getExternalUserId(), session.getStaffId());
        // 后续可通过 ChannelMessageRouter 推送评价卡片给用户
    }
}
