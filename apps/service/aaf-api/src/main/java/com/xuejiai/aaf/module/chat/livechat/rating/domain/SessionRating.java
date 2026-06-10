package com.xuejiai.aaf.module.chat.livechat.rating.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话评价实体。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "session_rating")
@SQLDelete(
        sql =
                "UPDATE session_rating SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class SessionRating extends BaseEntity {

    /** 关联会话 ID */
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** 评价用户 ID（访客） */
    @Column(name = "user_id")
    private Long userId;

    /** 被评价客服 ID */
    @Column(name = "staff_id")
    private Long staffId;

    /** 评分（1-5） */
    @Column(name = "score", nullable = false)
    private Integer score;

    /** 评价内容 */
    @Column(name = "comment", length = 512)
    private String comment;
}
