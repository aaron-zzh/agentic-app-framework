package com.xuejiai.aaf.module.ai.output.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.RiskLevel;
import com.xuejiai.aaf.module.ai.output.domain.AiOutput;
import com.xuejiai.aaf.module.ai.output.domain.enums.AiOutputStatus;
import com.xuejiai.aaf.module.ai.output.domain.enums.OutputCategory;
import com.xuejiai.aaf.module.ai.output.domain.enums.OutputSourceType;

/** AI 产出响应 VO。 */
public record AiOutputVO(
        Long id,
        Long sessionId,
        Long taskId,
        Long executionId,
        Long creatorId,
        OutputSourceType sourceType,
        OutputCategory category,
        RiskLevel riskLevel,
        String title,
        String description,
        String contentSnapshot,
        String revertInfo,
        AiOutputStatus status,
        String adjustNote,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static AiOutputVO from(AiOutput entity) {
        return new AiOutputVO(
                entity.getId(),
                entity.getSessionId(),
                entity.getTaskId(),
                entity.getExecutionId(),
                entity.getCreatorId(),
                entity.getSourceType(),
                entity.getCategory(),
                entity.getRiskLevel(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getContentSnapshot(),
                entity.getRevertInfo(),
                entity.getStatus(),
                entity.getAdjustNote(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }
}
