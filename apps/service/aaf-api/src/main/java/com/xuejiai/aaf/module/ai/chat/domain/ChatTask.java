package com.xuejiai.aaf.module.ai.chat.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 对话任务——与会话关联的持久化任务列表，助理可按队列逐个处理。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "ai_chat_task")
@SQLDelete(sql = "UPDATE ai_chat_task SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ChatTask extends BaseEntity {

    /** 所属会话 ID */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 创建者用户 ID */
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    /** 任务标题 */
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /** 任务详细描述 */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 状态：pending / running / done / failed / cancelled */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    /** 优先级（数值越小越优先） */
    @Column(name = "priority")
    private Integer priority = 0;

    /** 排序序号（同优先级内排序） */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /** 定时执行时间（为 null 表示立即可执行） */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /** 助理处理结果摘要 */
    @Column(name = "result", columnDefinition = "TEXT")
    private String result;
}
