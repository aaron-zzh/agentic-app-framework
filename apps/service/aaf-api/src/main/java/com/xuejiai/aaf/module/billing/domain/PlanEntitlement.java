package com.xuejiai.aaf.module.billing.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 套餐×权益规则（多对多关联，定价与权益解耦） */
@Getter
@Setter
@Entity
@Table(
        name = "billing_plan_entitlement",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_plan_ent",
                        columnNames = {"plan_id", "ent_id"}))
@SQLDelete(
        sql =
                "UPDATE billing_plan_entitlement SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class PlanEntitlement extends BaseEntity {

    /** 套餐 ID */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 权益定义 ID */
    @Column(name = "ent_id", nullable = false)
    private Long entId;

    /** 授予额度（COUNTABLE 用；-1=无限） */
    @Column(name = "quota", nullable = false)
    private Long quota;

    /** 重置周期（NONE/DAILY/MONTHLY/YEARLY） */
    @Column(name = "reset_cycle", nullable = false, length = 16)
    private String resetCycle;

    /** 额度用尽后单次充值价（积分），0=不可充值 */
    @Column(name = "refill_price", nullable = false)
    private Long refillPrice;
}
