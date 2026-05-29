package com.xuejiai.aaf.module.pay.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.pay.CreditRuleStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 积分转 Token 规则 */
@Getter
@Setter
@Entity
@Table(name = "credit_token_rule")
@SQLDelete(
        sql =
                "UPDATE credit_token_rule SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class CreditTokenRule extends BaseEntity {

    /** 规则名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 积分数量 */
    @Column(name = "credit_amount", nullable = false)
    private Long creditAmount;

    /** 可兑换 Token 数量 */
    @Column(name = "token_amount", nullable = false)
    private Long tokenAmount;

    /** 状态 */
    @Column(name = "status", nullable = false, length = 20)
    private String status = CreditRuleStatusEnum.ENABLED.getCode();

    /** 优先级（数值越小优先级越高） */
    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    /** 生效开始时间 */
    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    /** 生效结束时间 */
    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;
}
