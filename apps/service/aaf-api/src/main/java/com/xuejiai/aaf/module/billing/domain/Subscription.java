package com.xuejiai.aaf.module.billing.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户订阅实例（购买后产生，决定有效期）。
 *
 * <p>标注 {@link OrgIgnore}：订阅归属用户（{@code userId}）而非组织，用户切换组织/工作区不应 影响其订阅可见性，{@code org_id} 恒为 NULL。
 */
@Getter
@Setter
@Entity
@Table(name = "billing_subscription")
@SQLDelete(
        sql =
                "UPDATE billing_subscription SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
@OrgIgnore
public class Subscription extends BaseEntity {

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 套餐 ID */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 当前生效 SKU ID */
    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    /** 生效时间 */
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /** 到期时间（永久套餐为空） */
    @Column(name = "end_at")
    private LocalDateTime endAt;

    /** 状态（ACTIVE/EXPIRED/CANCELLED） */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 关联购买流水 ID */
    @Column(name = "source_id")
    private Long sourceId;

    /** 上次月度积分发放时间（防重复发放） */
    @Column(name = "last_credit_issued_at")
    private LocalDateTime lastCreditIssuedAt;

    /** 用户主动取消时间；NULL=未取消。取消后 status 仍 ACTIVE 直到 end_at。 */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** 排队待处理的降级目标 SKU；当前周期结束前不改变当前权益。 */
    @Column(name = "pending_sku_id")
    private Long pendingSkuId;

    /** 最近一次到期前提醒发送时间，幂等防止重复发送。 */
    @Column(name = "last_reminder_at")
    private LocalDateTime lastReminderAt;
}
