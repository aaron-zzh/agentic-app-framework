package com.xuejiai.aaf.module.digitalhuman.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Type;

import com.xuejiai.aaf.common.model.BaseEntity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * 积分消耗记录实体
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "credit_consumption")
@SQLDelete(sql = "UPDATE credit_consumption SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class CreditConsumption extends BaseEntity {

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 组织 ID */
    @Column(name = "org_id")
    private Long orgId;

    /** 工作空间 ID */
    @Column(name = "workspace_id")
    private Long workspaceId;

    /** 数字人 ID */
    @Column(name = "digital_human_id", nullable = false)
    private Long digitalHumanId;

    /** 操作类型 */
    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    /** 消耗积分数 */
    @Column(name = "credits", nullable = false)
    private Integer credits;

    /** 费用明细 */
    @Type(JsonType.class)
    @Column(name = "breakdown", columnDefinition = "jsonb")
    private CostBreakdown breakdown;

    /** 操作时间 */
    @Column(name = "operation_time", nullable = false)
    private LocalDateTime operationTime;

    /**
     * 费用明细
     */
    @Data
    public static class CostBreakdown {
        /** 基础费用 */
        private Integer baseCost;

        /** 时长费用 */
        private Integer durationCost;

        /** 附加费用 */
        private Integer additionalCost;

        /** 费用说明 */
        private String description;
    }
}
