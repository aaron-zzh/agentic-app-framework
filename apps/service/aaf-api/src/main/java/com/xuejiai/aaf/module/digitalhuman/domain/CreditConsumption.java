package com.xuejiai.aaf.module.digitalhuman.domain;

import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * 数字人积分消耗记录实体
 *
 * <p>记录形象生成、视频生成等操作的积分消耗明细，用于统计分析和成本核算。消费用户为 {@link BaseEntity#getOwnerId()}。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "digital_human_credit_consumption")
@SQLDelete(
        sql =
                "UPDATE digital_human_credit_consumption SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class CreditConsumption extends BaseEntity {

    /** 关联的数字人 ID */
    @Column(name = "digital_human_id")
    private Long digitalHumanId;

    /** 关联的项目 ID */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 操作类型：avatar_generation / simple_video / complex_video */
    @Column(name = "operation", nullable = false, length = 64)
    private String operation;

    /** 消耗的积分数 */
    @Column(name = "credits_consumed", nullable = false)
    private Integer creditsConsumed;

    /** 成本明细 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "breakdown", columnDefinition = "jsonb")
    private CostBreakdown breakdown;

    /** 成本明细 */
    @Data
    public static class CostBreakdown {
        /** API 提供商 */
        private String apiProvider;

        /** API 实际费用 */
        private Double apiCost;

        /** 视频时长（秒） */
        private Integer duration;

        /** 场景数量 */
        private Integer sceneCount;

        /** 包含的元素 */
        private List<String> elements;
    }
}
