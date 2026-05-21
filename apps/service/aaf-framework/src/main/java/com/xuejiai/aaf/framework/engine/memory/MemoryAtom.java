/**
 * 原子记忆单元——记忆引擎的最小存储粒度。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.engine.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 记忆原子：不可再分的最小记忆单元。 双时态模型：event_time（事件发生时间）+ valid_from/valid_to（记忆有效窗口）。 */
@Getter
@Setter
@Entity
@Table(
        name = "memory_atom",
        indexes = {
            @Index(columnList = "userId,scope"),
            @Index(columnList = "userId,eventTime DESC"),
            @Index(columnList = "userId,weight DESC"),
            @Index(columnList = "validTo")
        })
public class MemoryAtom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 所属用户 */
    @Column(nullable = false)
    private Long userId;

    /** 记忆范围：short_term / long_term / episodic / procedural */
    @Column(nullable = false, length = 20)
    private String scope;

    /** 原子内容（文本片段） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 向量表示（由 PgVector 存储） */
    @Column(columnDefinition = "vector(1536)")
    private float[] embedding;

    /** 事件发生时间（双时态） */
    @Column(nullable = false)
    private Instant eventTime;

    /** 记忆生效时间（双时态） */
    @Column(nullable = false)
    private Instant validFrom;

    /** 记忆失效时间（null = 当前有效）（双时态） */
    private Instant validTo;

    /** 价值权重 0.0~1.0（用于遗忘决策） */
    @Column(nullable = false)
    private Double weight = 0.5;

    /** 访问次数 */
    @Column(nullable = false)
    private Integer accessCount = 0;

    /** 最后访问时间 */
    private Instant lastAccessedAt;

    /** 分类标签 */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> tags;

    /** 扩展元数据 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /** 写入时间 */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.validFrom == null) this.validFrom = now;
        if (this.eventTime == null) this.eventTime = now;
        if (this.lastAccessedAt == null) this.lastAccessedAt = now;
    }
}
