package com.xuejiai.aaf.module.ai.digitalhuman.domain;

import com.xuejiai.aaf.common.model.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Type;

/**
 * 积分消耗记录
 *
 * <p>记录数字人相关操作的积分消耗明细，包括形象生成、视频生成等。
 * 用于统计分析和成本核算。
 */
@Getter
@Setter
@Entity
@Table(name = "credit_consumption")
@SQLDelete(sql = "UPDATE credit_consumption SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class CreditConsumption extends BaseEntity {

    /** 关联的数字人 ID */
    @Column(name = "digital_human_id")
    private Long digitalHumanId;

    /** 关联的项目 ID */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 操作类型：avatar_generation-形象生成, simple_video-简单视频, complex_video-复杂视频 */
    @Column(name = "operation", nullable = false, length = 64)
    private String operation;

    /** 消耗的积分数 */
    @Column(name = "credits_consumed", nullable = false)
    private Integer creditsConsumed;

    /** 成本明细（API 提供商、费用、时长、场景数等） */
    @Type(JsonType.class)
    @Column(name = "breakdown", columnDefinition = "jsonb")
    private CostBreakdown breakdown;

    /**
     * 成本明细
     */
    @Getter
    @Setter
    public static class CostBreakdown {
        private String apiProvider;     // aliyun, hyperframes
        private Double apiCost;         // API 实际费用（美元）
        private Integer duration;       // 视频时长（秒）
        private Integer sceneCount;     // 场景数量
        private String[] elements;      // 包含的元素（background_music, text_overlay等）
    }
}
