package com.xuejiai.aaf.module.billing.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 成长等级（免费线，按 exp 自动升降） */
@Getter
@Setter
@Entity
@Table(name = "level")
@SQLDelete(
        sql =
                "UPDATE level SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class Level extends BaseEntity {

    /** 等级编码（L0/L1/L2） */
    @Column(name = "code", nullable = false, unique = true, length = 16)
    private String code;

    /** 等级名称 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 经验值下限（含） */
    @Column(name = "exp_min", nullable = false)
    private Integer expMin;

    /** 经验值上限（含） */
    @Column(name = "exp_max", nullable = false)
    private Integer expMax;

    /** 成长小权益（JSONB，签到加成倍率等） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "perks", columnDefinition = "jsonb")
    private String perks;

    /** 排序 */
    @Column(name = "sort", nullable = false)
    private Integer sort;
}
