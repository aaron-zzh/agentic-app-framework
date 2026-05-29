package com.xuejiai.aaf.module.company.planning.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/** 企业战略规划 */
@Getter
@Setter
@Entity
@Table(name = "company_plan")
@SQLDelete(sql = "UPDATE company_plan SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class CompanyPlan extends BaseEntity {

    /** 规划名称 */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** 规划类型（STRATEGY/PRODUCT/GROWTH/FINANCE） */
    @Column(name = "plan_type", nullable = false, length = 32)
    private String planType;

    /** 规划周期（QUARTERLY/YEARLY） */
    @Column(name = "period", nullable = false, length = 16)
    private String period;

    /** 年份 */
    @Column(name = "year", nullable = false)
    private Integer year;

    /** 季度（1-4，年度规划为空） */
    @Column(name = "quarter")
    private Integer quarter;

    /** 规划内容（Markdown） */
    @Column(name = "content", columnDefinition = "text")
    private String content;

    /** 状态（DRAFT/ACTIVE/COMPLETED/ARCHIVED） */
    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
