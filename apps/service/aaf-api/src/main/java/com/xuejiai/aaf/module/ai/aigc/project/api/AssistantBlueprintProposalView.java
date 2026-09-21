package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** Assistant Blueprint Proposal 的安全查询视图。 */
public record AssistantBlueprintProposalView(
        String planningId,
        String proposalId,
        Integer proposalVersion,
        String proposalHash,
        String projectName,
        AssistantBlueprintCreationContext creationContext,
        String status,
        AigcProjectBlueprintSnapshotPayload normalizedSnapshot,
        Map<String, Object> validationResult,
        String sourceTaskId,
        String sourceExecutionId,
        BigDecimal estimatedCredits,
        LocalDateTime expiresAt,
        LocalDateTime confirmedTime,
        Long linkedProjectId) {

    public AssistantBlueprintProposalView {
        validationResult = validationResult == null ? Map.of() : Map.copyOf(validationResult);
    }
}
