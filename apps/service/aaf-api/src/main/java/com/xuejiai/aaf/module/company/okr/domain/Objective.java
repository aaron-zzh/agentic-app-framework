package com.xuejiai.aaf.module.company.okr.domain;

import java.math.BigDecimal;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** OKR 目标 */
@Getter
@Setter
@Entity
@Table(name = "company_objective")
@SQLDelete(
        sql =
                "UPDATE company_objective SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Objective extends BaseEntity {

    /** 目标标题 */
    @Column(name = "title", nullable = false, length = 256)
    private String title;

    /** 所属规划 ID */
    @Column(name = "plan_id")
    private Long planId;

    /** 父目标 ID（支持层级） */
    @Column(name = "parent_id")
    private Long parentId;

    /** 负责人 ID */
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    /** 进度（0-100） */
    @Column(name = "progress", precision = 5, scale = 2)
    private BigDecimal progress;

    /** 状态（NOT_STARTED/IN_PROGRESS/AT_RISK/COMPLETED） */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 周期（如 2026-Q2） */
    @Column(name = "period", length = 16)
    private String period;
}
