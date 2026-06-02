package com.xuejiai.aaf.module.billing.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 订阅购买流水（新购/续费） */
@Getter
@Setter
@Entity
@Table(name = "subscription_record")
@SQLDelete(
        sql =
                "UPDATE subscription_record SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class SubscriptionRecord extends BaseEntity {

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 套餐 ID */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 操作类型（NEW/RENEW） */
    @Column(name = "operation", nullable = false, length = 16)
    private String operation;

    /** 关联支付订单 ID */
    @Column(name = "pay_order_id")
    private Long payOrderId;

    /** 支付金额（分） */
    @Column(name = "pay_price", nullable = false)
    private Long payPrice;

    /** 支付状态（UNPAID/PAID） */
    @Column(name = "pay_status", nullable = false, length = 16)
    private String payStatus;

    /** 支付时间 */
    @Column(name = "pay_time")
    private LocalDateTime payTime;
}
