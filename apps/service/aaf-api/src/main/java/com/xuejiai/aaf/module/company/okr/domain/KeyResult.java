package com.xuejiai.aaf.module.company.okr.domain;

import java.math.BigDecimal;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** OKR 关键结果 */
@Getter
@Setter
@Entity
@Table(name = "company_key_result")
@SQLDelete(sql = "UPDATE company_key_result SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class KeyResult extends BaseEntity {

    /** 所属目标 ID */
    @Column(name = "objective_id", nullable = false)
    private Long objectiveId;

    /** 关键结果描述 */
    @Column(name = "title", nullable = false, length = 256)
    private String title;

    /** 度量类型（PERCENTAGE/NUMBER/CURRENCY/BOOLEAN） */
    @Column(name = "metric_type", nullable = false, length = 16)
    private String metricType;

    /** 起始值 */
    @Column(name = "start_value", precision = 18, scale = 4)
    private BigDecimal startValue;

    /** 目标值 */
    @Column(name = "target_value", precision = 18, scale = 4)
    private BigDecimal targetValue;

    /** 当前值 */
    @Column(name = "current_value", precision = 18, scale = 4)
    private BigDecimal currentValue;

    /** 负责人 ID */
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    /** 状态 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
