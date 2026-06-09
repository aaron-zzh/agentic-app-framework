package com.xuejiai.aaf.module.billing.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 积分充值套餐 */
@Getter
@Setter
@Entity
@Table(name = "credit_package")
public class CreditPackage extends BaseEntity {

    /** 套餐名称 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 积分数 */
    @Column(name = "credits", nullable = false)
    private Long credits;

    /** 赠送积分 */
    @Column(name = "bonus_credits", nullable = false)
    private Long bonusCredits = 0L;

    /** 售价（分） */
    @Column(name = "price", nullable = false)
    private Long price;

    /** 套餐分组标签 */
    @Column(name = "group_label", length = 32)
    private String groupLabel;

    /** 是否推荐 */
    @Column(name = "recommended", nullable = false)
    private Boolean recommended = false;

    /** 状态（ENABLED/DISABLED） */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "ENABLED";

    /** 排序 */
    @Column(name = "sort", nullable = false)
    private Integer sort = 0;
}
