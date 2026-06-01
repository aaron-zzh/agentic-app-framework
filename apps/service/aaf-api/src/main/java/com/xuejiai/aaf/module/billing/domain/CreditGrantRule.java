package com.xuejiai.aaf.module.billing.domain;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

/**
 * 积分发放规则（运营后台配置）。
 *
 * <p>定义各类积分来源的发放数量和有效期，供业务代码按 code 查询后调用 CreditService.earnBatch()。
 */
@Getter
@Setter
@Entity
@Table(name = "credit_grant_rule")
@SQLDelete(sql = "UPDATE credit_grant_rule SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class CreditGrantRule extends BaseEntity {

    /** 规则编码（WEEKLY/INVITE/EXPLORE/REGISTER/EVENT_xxx） */
    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    /** 规则名称 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 发放积分数 */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /** 有效天数（0 = 永久） */
    @Column(name = "expire_days", nullable = false)
    private Integer expireDays = 0;

    /** 触发方式：SCHEDULE / EVENT / MANUAL */
    @Column(name = "trigger", nullable = false, length = 16)
    private String trigger;

    /** 状态：ENABLED / DISABLED */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "ENABLED";

    /** 生效开始时间（null = 立即生效） */
    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    /** 生效结束时间（null = 永久有效） */
    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    /** 计算过期时间：expireDays=0 返回 null（永久） */
    public LocalDateTime calcExpireAt() {
        return expireDays > 0 ? LocalDateTime.now().plusDays(expireDays) : null;
    }

    /**
     * 扩展配置（JSONB）。
     *
     * <p>示例：{"max_per_user": 1, "description": "每位用户仅限一次"}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ext", columnDefinition = "jsonb")
    private String ext;
}
