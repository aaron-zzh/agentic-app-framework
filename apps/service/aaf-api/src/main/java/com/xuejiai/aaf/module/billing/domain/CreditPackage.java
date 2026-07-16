package com.xuejiai.aaf.module.billing.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 积分充值套餐。
 *
 * <p>标注 {@link SQLDelete}：套餐一旦被购买过，历史订单（{@code CreditRechargePayHandler}） 可能引用套餐 ID
 * 用于展示/追溯，物理删除会破坏这类历史数据的可追溯性，需软删除。 与同类计费配置实体（{@code SubscriptionPlan}/{@code Level}）保持一致。
 *
 * <p>标注 {@link OrgIgnore}：套餐是平台级货架商品定义，不含用户/组织归属字段，{@code org_id} 恒为 NULL，与 {@code
 * CreditGrantRule}/{@code EntitlementDef} 同类。
 */
@Getter
@Setter
@Entity
@Table(name = "credit_package")
@SQLDelete(
        sql =
                "UPDATE credit_package SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
@OrgIgnore
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
