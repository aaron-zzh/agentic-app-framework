package com.xuejiai.aaf.module.livechat.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话满意度评价实体。
 *
 * <p>会话关闭后用户对本次服务的评分和评论。
 */
@Getter
@Setter
@Entity
@Table(name = "session_rating")
@SQLDelete(sql = "UPDATE session_rating SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class SessionRating extends BaseEntity {

    /** 关联会话 ID */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 评价用户 ID */
    @Column(name = "user_id")
    private Long userId;

    /** 被评价坐席 ID */
    @Column(name = "staff_id")
    private Long staffId;

    /** 评分（1-5） */
    @Column(name = "score", nullable = false)
    private Integer score;

    /** 评价内容 */
    @Column(name = "comment", length = 512)
    private String comment;
}
