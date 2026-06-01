package com.xuejiai.aaf.module.billing.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

/** 订阅套餐定义（货架商品） */
@Getter
@Setter
@Entity
@Table(name = "subscription_plan")
@SQLDelete(sql = "UPDATE subscription_plan SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class SubscriptionPlan extends BaseEntity {

    /** 套餐编码（FREE/PRO/TEAM/ENTERPRISE） */
    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    /** 套餐名称 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 有效天数（FREE 为 0 表示永久） */
    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    /** 售价（分） */
    @Column(name = "price", nullable = false)
    private Long price;

    /** 市场价/划线价（分） */
    @Column(name = "market_price", nullable = false)
    private Long marketPrice;

    /** 状态（ENABLED/DISABLED） */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 排序 */
    @Column(name = "sort", nullable = false)
    private Integer sort;

    /** 每月发放积分数（0 = 不发放） */
    @Column(name = "monthly_credits", nullable = false)
    private Long monthlyCredits = 0L;

    /**
     * 扩展配置（JSONB）。
     *
     * <p>示例：{"highlight": true, "badge": "热门", "features": ["无限对话", "优先客服"]}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ext", columnDefinition = "jsonb")
    private String ext;
}
