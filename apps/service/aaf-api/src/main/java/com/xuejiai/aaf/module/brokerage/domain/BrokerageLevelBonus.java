package com.xuejiai.aaf.module.brokerage.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 会员等级佣金加成。分销员自身订阅套餐决定能拿的佣金比例，覆盖 brokerage_rule 的基础比例。 */
@Getter
@Setter
@Entity
@Table(name = "brokerage_level_bonus")
@SQLDelete(
        sql =
                "UPDATE brokerage_level_bonus SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class BrokerageLevelBonus extends BaseEntity {

    /** 关联 brokerage_rule.id */
    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    /** 关联 billing_subscription_plan.id（分销员自己的套餐） */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 覆盖 rule 的一级佣金比例 */
    @Column(name = "level1_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal level1Rate;

    /** 覆盖 rule 的二级佣金比例 */
    @Column(name = "level2_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal level2Rate;
}
