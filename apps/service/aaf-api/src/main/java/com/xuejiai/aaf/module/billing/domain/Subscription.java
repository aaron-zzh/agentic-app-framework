package com.xuejiai.aaf.module.billing.domain;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** 用户订阅实例（购买后产生，决定有效期） */
@Getter
@Setter
@Entity
@Table(name = "subscription")
@SQLDelete(sql = "UPDATE subscription SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
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
}
