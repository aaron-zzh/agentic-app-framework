package com.xuejiai.aaf.module.system.notify.event;

import lombok.Getter;

/**
 * @提及事件，在评论中 @用户时发布。
 *
 * @author AaronZZH & Kiro
 */
@Getter
public class MentionEvent {

    /** 被提及的用户 ID */
    private final Long mentionedUserId;

    /** 操作者昵称 */
    private final String actorName;

    /** 关联实体类型 */
    private final String entityType;

    /** 关联实体 ID */
    private final Long entityId;

    /** 评论内容摘要（最多 50 字） */
    private final String excerpt;

    /** 前端跳转链接 */
    private final String relatedUrl;

    public MentionEvent(
            Long mentionedUserId,
            String actorName,
            String entityType,
            Long entityId,
            String excerpt) {
        this.mentionedUserId = mentionedUserId;
        this.actorName = actorName;
        this.entityType = entityType;
        this.entityId = entityId;
        this.excerpt = excerpt;
        this.relatedUrl = "/entities/%s/%d".formatted(entityType, entityId);
    }
}
