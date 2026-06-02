package com.xuejiai.aaf.module.billing.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 用户权益额度实例（订阅生效时按 plan_entitlement 实例化） */
@Getter
@Setter
@Entity
@Table(
        name = "entitlement_quota",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_quota_user_ent",
                        columnNames = {"user_id", "ent_id"}))
@SQLDelete(
        sql =
                "UPDATE entitlement_quota SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class EntitlementQuota extends BaseEntity {

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 权益定义 ID */
    @Column(name = "ent_id", nullable = false)
    private Long entId;

    /** 本周期总额度 */
    @Column(name = "total", nullable = false)
    private Long total;

    /** 已用 */
    @Column(name = "used", nullable = false)
    private Long used;

    /** 剩余 */
    @Column(name = "remain", nullable = false)
    private Long remain;

    /** 上次重置时间 */
    @Column(name = "last_reset_at")
    private LocalDateTime lastResetAt;

    /** 下次重置时间（定时任务扫描） */
    @Column(name = "next_reset_at")
    private LocalDateTime nextResetAt;
}
