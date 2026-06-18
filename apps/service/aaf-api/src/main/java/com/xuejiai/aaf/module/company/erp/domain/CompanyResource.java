package com.xuejiai.aaf.module.company.erp.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 企业资源（轻量 ERP） */
@Getter
@Setter
@Entity
@Table(name = "company_resource")
@SQLDelete(
        sql =
                "UPDATE company_resource SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class CompanyResource extends BaseEntity {

    /** 资源名称 */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** 资源类型（BUDGET/HEADCOUNT/TOOL/LICENSE） */
    @Column(name = "resource_type", nullable = false, length = 32)
    private String resourceType;

    /** 总量 */
    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    /** 已使用量 */
    @Column(name = "used_amount", precision = 18, scale = 2)
    private BigDecimal usedAmount;

    /** 单位 */
    @Column(name = "unit", length = 32)
    private String unit;

    /** 所属部门/项目 */
    @Column(name = "department", length = 64)
    private String department;

    /** 状态（AVAILABLE/EXHAUSTED/FROZEN） */
    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
