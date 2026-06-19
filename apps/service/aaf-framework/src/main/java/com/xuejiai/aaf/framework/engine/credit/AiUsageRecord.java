package com.xuejiai.aaf.framework.engine.credit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 调用用量记录——每次 AI 能力调用结算时写入一条。
 *
 * <p>与 {@link CreditTransaction} 通过 {@link #creditTxId} 关联： CreditTransaction
 * 记录积分账务（扣了多少分），本表记录原始用量和成本（消耗了多少 token/秒/单元，花了多少钱）。
 */
@Getter
@Setter
@Entity
@Table(
        name = "ai_usage_record",
        indexes = {
            @Index(name = "idx_air_user_id", columnList = "user_id"),
            @Index(name = "idx_air_model_id", columnList = "model_id"),
            @Index(name = "idx_air_capability", columnList = "capability"),
            @Index(name = "idx_air_create_time", columnList = "create_time")
        })
public class AiUsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 模型 ID（ai_model.id） */
    @Column(name = "model_id")
    private Long modelId;

    /** 能力标识（如 "ocr"/"image-gen"/"music-gen"/"video-gen"） */
    @Column(name = "capability", nullable = false, length = 32)
    private String capability;

    /** 计费类型（对应 AiQuotaTypeEnum）：0=TOKEN，1=PER_USE，2=PER_SEC，3=PER_UNIT。 决定如何解读 usage 字段。 */
    @Column(name = "quota_type", nullable = false)
    private Short quotaType;

    /** 本次实际成本（元，保留 6 位小数） */
    @Column(name = "cost_yuan", precision = 14, scale = 6)
    private BigDecimal costYuan;

    /** 扣减积分数（与关联 CreditTransaction.amount 一致） */
    @Column(name = "credit_amount")
    private Long creditAmount;

    /** 关联积分流水 ID */
    @Column(name = "credit_tx_id")
    private Long creditTxId;

    /**
     * 标准化用量（jsonb）——按 quotaType 结构不同：
     *
     * <ul>
     *   <li>TOKEN: {@code {"inputTokens":1200, "outputTokens":800}}
     *   <li>PER_USE: {@code {"count":1}}
     *   <li>PER_SEC: {@code {"duration":32}}
     *   <li>PER_UNIT: {@code {"resolution":"1080p", "count":1}}
     * </ul>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "usage", columnDefinition = "jsonb")
    private String usage;

    /**
     * 供应商原始 usage 字段（jsonb）——API 响应中的 usage 对象，原样存储，用于审计和对账。
     *
     * <p>示例（视频生成）： {@code
     * {"duration":5,"input_video_duration":0,"output_video_duration":5,"video_count":1,"SR":720,"ratio":"16:9"}}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_usage", columnDefinition = "jsonb")
    private String rawUsage;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    void prePersist() {
        if (createTime == null) createTime = LocalDateTime.now();
    }
}
