package com.xuejiai.aaf.module.billing.domain;

import java.time.LocalDateTime;

import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 权益额度变更流水（账本，每次扣减/充值/重置留痕）。
 *
 * <p>不继承 {@link com.xuejiai.aaf.common.model.BaseEntity}：账本语义上只应追加（append-only）， 不提供软删除/组织过滤能力（无
 * {@code deleted}/{@code org_id} 列）。
 *
 * <p>标注 {@link OrgIgnore}：{@code OrgFilterAspect} 是覆盖所有 {@code module.repository}
 * 方法的全局切面，不检查目标表是否真的存在 {@code org_id} 列——只要调用方（{@code BillingQueryService}/{@code
 * EntitlementService}，均在登录后 HTTP 请求链路中触发）缺少组织上下文， 未标注即会被 fail-closed 拒绝（403），而非"因无 org_id 列自动豁免"。
 */
@Getter
@Setter
@Entity
@Table(name = "billing_entitlement_ledger")
@OrgIgnore
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
