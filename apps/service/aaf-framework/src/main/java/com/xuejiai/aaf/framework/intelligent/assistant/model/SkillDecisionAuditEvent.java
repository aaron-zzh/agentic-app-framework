package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Role 选择、Skill 选择、激活和执行画像冻结阶段的追加式审计事件。 */
public record SkillDecisionAuditEvent(
        String tenantId,
        Long orgId,
        Long workspaceId,
        String assistantId,
        String conversationId,
        String sessionId,
        String taskId,
        String executionId,
        String runId,
        String roleKey,
        String selectionMode,
        String eventType,
        String skillCode,
        Long skillVersionId,
        List<String> candidateSkillCodes,
        String reasonCode,
        String reasonDetail,
        String selectedBy,
        List<String> effectiveToolRefs,
        String modelSpec,
        String traceId,
        String correlationId,
        Instant occurredAt) {

    public SkillDecisionAuditEvent {
        Objects.requireNonNull(eventType, "eventType 不能为空");
        candidateSkillCodes =
                List.copyOf(candidateSkillCodes == null ? List.of() : candidateSkillCodes);
        effectiveToolRefs = List.copyOf(effectiveToolRefs == null ? List.of() : effectiveToolRefs);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
