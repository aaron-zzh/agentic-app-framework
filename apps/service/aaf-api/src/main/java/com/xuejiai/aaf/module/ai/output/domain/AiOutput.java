package com.xuejiai.aaf.module.ai.output.domain;

import com.xuejiai.aaf.common.enums.RiskLevel;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.module.ai.output.domain.enums.AiOutputStatus;
import com.xuejiai.aaf.module.ai.output.domain.enums.OutputCategory;
import com.xuejiai.aaf.module.ai.output.domain.enums.OutputSourceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** AI 产出记录——所有助理工作成果的统一归档。 */
@Getter
@Setter
@Entity
@Table(name = "ai_output")
public class AiOutput extends BaseEntity {

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "execution_id")
    private Long executionId;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    /** 来源 */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private OutputSourceType sourceType;

    /** 类别 */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private OutputCategory category;

    /** 风险等级 */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 10)
    private RiskLevel riskLevel = RiskLevel.LOW;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 内容快照 JSON */
    @Column(name = "content_snapshot", columnDefinition = "JSONB")
    private String contentSnapshot;

    /** 回退信息 JSON（变更前状态） */
    @Column(name = "revert_info", columnDefinition = "JSONB")
    private String revertInfo;

    /** 状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiOutputStatus status = AiOutputStatus.EFFECTIVE;

    @Column(name = "adjust_note", columnDefinition = "TEXT")
    private String adjustNote;
}
