package com.xuejiai.aaf.module.ai.output.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 产出记录——所有助理工作成果的统一归档。
 */
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

    /** 来源：autodev / task / chat / tool */
    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    /** 类别：code / document / entity_change / config / file */
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    /** 风险：high / medium / low */
    @Column(name = "risk_level", nullable = false, length = 10)
    private String riskLevel = "low";

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

    /** effective / adjusted / reverted */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "effective";

    @Column(name = "adjust_note", columnDefinition = "TEXT")
    private String adjustNote;
}
