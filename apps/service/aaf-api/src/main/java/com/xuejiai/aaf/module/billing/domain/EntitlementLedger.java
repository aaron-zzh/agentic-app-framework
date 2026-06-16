package com.xuejiai.aaf.module.billing.domain;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 权益额度变更流水（账本，每次扣减/充值/重置留痕） */
@Getter
@Setter
@Entity
@Table(name = "billing_entitlement_ledger")
public class EntitlementLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联额度实例 ID */
    @Column(name = "quota_id", nullable = false)
    private Long quotaId;

    /** 变化量（负数=消费，正数=充值/重置） */
    @Column(name = "delta", nullable = false)
    private Long delta;

    /** 操作类型（USE/REFILL/RESET/ADJUST） */
    @Column(name = "operation", nullable = false, length = 16)
    private String operation;

    /** 业务类型（AI_CALL/KB_UPLOAD/MANUAL 等） */
    @Column(name = "biz_type", length = 24)
    private String bizType;

    /** 业务 ID */
    @Column(name = "biz_id")
    private Long bizId;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
