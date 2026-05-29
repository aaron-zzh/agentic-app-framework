package com.xuejiai.aaf.module.company.ops.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** 运营指标 */
@Getter
@Setter
@Entity
@Table(name = "company_ops_metric")
@SQLDelete(sql = "UPDATE company_ops_metric SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class OpsMetric extends BaseEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** 指标编码（唯一） */
    @Column(name = "code", nullable = false, length = 64, unique = true)
    private String code;

    @Column(name = "value", nullable = false, precision = 18, scale = 4)
    private BigDecimal value;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "source", length = 64)
    private String source;
}
