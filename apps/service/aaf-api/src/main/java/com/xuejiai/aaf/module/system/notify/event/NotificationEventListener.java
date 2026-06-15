package com.xuejiai.aaf.module.system.notify.event;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.messaging.internal.InternalMessage;
import com.xuejiai.aaf.framework.messaging.internal.InternalMessageSender;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通知事件监听器，统一将业务事件转为站内信 + WS 推送。
 *
 * <p>异步执行，不阻塞业务事务。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final InternalMessageSender messageSender;
    private final UserRepository userRepository;

    /** 处理 @提及事件 */
    @Async
    @EventListener
    public void onMention(MentionEvent event) {
        messageSender.send(
                InternalMessage.builder()
                        .userId(event.getMentionedUserId())
                        .type("mention")
                        .title(event.getActorName() + " 在评论中提到了你")
                        .body(event.getExcerpt())
                        .relatedUrl(event.getRelatedUrl())
                        .entityType(event.getEntityType())
                        .entityId(event.getEntityId())
                        .build());
    }

    /** 处理公告发布事件，推送给所有用户（只存库，避免推送风暴） */
    @Async
    @EventListener
    public void onNoticePublished(NoticePublishedEvent event) {
        List<Long> userIds = userRepository.findSimpleList().stream().map(u -> u.id()).toList();
        userIds.forEach(
                userId ->
                        messageSender.send(
                                InternalMessage.builder()
                                        .userId(userId)
                                        .type("system")
                                        .title("【公告】" + event.getTitle())
                                        .body(event.getExcerpt())
                                        .relatedUrl(event.getRelatedUrl())
                                        .strategy(InternalMessage.PushStrategy.PERSIST_ONLY)
                                        .build()));
        log.info("公告 {} 已推送给 {} 个用户", event.getNoticeId(), userIds.size());
    }
}
