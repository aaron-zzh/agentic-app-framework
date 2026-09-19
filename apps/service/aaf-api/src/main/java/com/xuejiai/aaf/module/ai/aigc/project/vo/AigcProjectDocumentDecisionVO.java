package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.time.LocalDateTime;

/** 项目文档版本候选及决策审计投影。 */
public record AigcProjectDocumentDecisionVO(
        Long id,
        Integer version,
        Long projectDocumentRefId,
        Long documentVersionId,
        Integer documentVersionNo,
        String title,
        String decision,
        String sourceType,
        String sourceRef,
        String decisionMode,
        String decisionReason,
        Long decidedBy,
        String decidedByType,
        LocalDateTime decidedTime,
        Long replacedDocumentVersionId,
        LocalDateTime createTime) {}
