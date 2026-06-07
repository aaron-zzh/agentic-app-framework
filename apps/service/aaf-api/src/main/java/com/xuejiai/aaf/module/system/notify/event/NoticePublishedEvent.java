package com.xuejiai.aaf.module.system.notify.event;

import lombok.Getter;

/**
 * 公告发布事件，管理员发布公告时发布。
 *
 * @author AaronZZH & Kiro
 */
@Getter
public class NoticePublishedEvent {

    /** 公告 ID */
    private final Long noticeId;

    /** 公告标题 */
    private final String title;

    /** 公告内容摘要 */
    private final String excerpt;

    /** 前端跳转链接 */
    private final String relatedUrl;

    public NoticePublishedEvent(Long noticeId, String title, String content) {
        this.noticeId = noticeId;
        this.title = title;
        this.excerpt =
                content != null && content.length() > 80 ? content.substring(0, 80) + "…" : content;
        this.relatedUrl = "/notices/" + noticeId;
    }
}
