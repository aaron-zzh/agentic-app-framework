package com.xuejiai.aaf.module.billing.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 用户订阅实例（购买后产生，决定有效期） */
@Getter
@Setter
@Entity
@Table(name = "billing_subscription")
@SQLDelete(
        sql =
                "UPDATE billing_subscription SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Subscription extends BaseEntity {

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 套餐 ID */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

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

    /**
     * 自动续费意图位：FALSE=用户已取消，到期不续费。
     *
     * <p>本期不实现渠道代扣，仅做意图记录与未来扩展位（详见 membership-completion.md 自动续费扩展点）。
     */
    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew = true;

    /** 用户主动取消时间；NULL=未取消。取消后 status 仍 ACTIVE 直到 end_at。 */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** 排队待切换的下一套餐 ID（降级用）；end_at 到期时若非空，自动激活该套餐。 */
    @Column(name = "pending_plan_id")
    private Long pendingPlanId;

    /** 排队待切换是否年付（与 pendingPlanId 配套）。 */
    @Column(name = "pending_yearly", nullable = false)
    private Boolean pendingYearly = false;

    /** 最近一次到期前提醒发送时间，幂等防止重复发送。 */
    @Column(name = "last_reminder_at")
    private LocalDateTime lastReminderAt;
}
