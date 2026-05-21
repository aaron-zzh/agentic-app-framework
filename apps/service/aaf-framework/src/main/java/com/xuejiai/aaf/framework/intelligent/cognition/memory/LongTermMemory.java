/**
 * 长期记忆实体。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 长期记忆：持久化的用户记忆片段，支持重要性评分和衰减。 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_long_term_memory",
        indexes = {
            @Index(columnList = "userId,importance DESC"),
            @Index(columnList = "userId,createdAt DESC")
        })
public class LongTermMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 */
    @Column(nullable = false)
    private Long userId;

    /** 记忆内容（压缩后的摘要） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 原始来源（对话 ID 或事件 ID） */
    @Column(length = 128)
    private String sourceId;

    /** 记忆类型：episodic(情景) / semantic(语义) / emotional(情感) */
    @Column(nullable = false, length = 32)
    private String memoryType;

    /** 重要性评分 0.0~1.0 */
    @Column(nullable = false)
    private Double importance = 0.5;

    /** 最后访问时间（用于衰减计算） */
    private LocalDateTime lastAccessedAt;

    /** 访问次数 */
    @Column(nullable = false)
    private Integer accessCount = 0;

    /** 事件时间（双时态：事件实际发生时间） */
    private LocalDateTime eventTime;

    /** 写入时间（双时态：记忆写入时间） */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = this.createdAt;
    }
}
