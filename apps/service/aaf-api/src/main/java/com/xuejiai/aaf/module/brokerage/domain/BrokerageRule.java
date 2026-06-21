package com.xuejiai.aaf.module.brokerage.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 佣金规则。按 biz_type + 目标精确匹配，priority 越小优先级越高。 */
@Getter
@Setter
@Entity
@Table(name = "brokerage_rule")
@SQLDelete(
        sql =
                "UPDATE brokerage_rule SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class BrokerageRule extends BaseEntity {

    /** 规则名称 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 业务类型：ORDER/SUBSCRIBE/RECHARGE/INVITE */
    @Column(name = "biz_type", nullable = false, length = 32)
    private String bizType;

    /** 目标类型：PRODUCT/PLAN/PACKAGE，null=全部 */
    @Column(name = "biz_target_type", length = 32)
    private String bizTargetType;

    /** 具体目标 ID，null=全部 */
    @Column(name = "biz_target_id", length = 64)
    private String bizTargetId;

    /** 一级佣金比例（0.1000=10%） */
    @Column(name = "level1_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal level1Rate = BigDecimal.ZERO;

    /** 二级佣金比例 */
    @Column(name = "level2_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal level2Rate = BigDecimal.ZERO;

    /** 计算基准：AMOUNT=按金额 / FIXED=固定金额 */
    @Column(name = "calc_base", nullable = false, length = 16)
    private String calcBase = "AMOUNT";

    /** 固定佣金金额（分），calc_base=FIXED 时有效 */
    @Column(name = "fixed_amount")
    private Long fixedAmount;

    /** 佣金冻结天数 */
    @Column(name = "frozen_days", nullable = false)
    private Integer frozenDays = 7;

    /** 优先级，数字越小越优先 */
    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    /** 状态：ENABLED/DISABLED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "ENABLED";
}
