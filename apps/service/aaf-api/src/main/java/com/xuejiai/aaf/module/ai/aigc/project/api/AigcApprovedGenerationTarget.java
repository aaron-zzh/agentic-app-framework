package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * 已确认 Proposal 目标的服务端解析结果；仅供 canonical Task/执行桥接冻结，不作为 REST 客户端输入。
 *
 * <p>{@code CREATION_DRAFT} 域尚无项目，因此 {@code projectId}/{@code projectVersion}/{@code
 * graphRevision}/ {@code configurationSnapshot*} 均为空，改由 {@code draftId} + {@code
 * blueprintVersionId} 锚定。
 */
public record AigcApprovedGenerationTarget(
        AigcGenerationProposalScope.Kind scopeKind,
        Long projectId,
        String draftId,
        Long blueprintVersionId,
        Integer projectVersion,
        Long graphRevision,
        Long configurationSnapshotId,
        String configurationSnapshotHash,
        String proposalId,
        Integer proposalVersion,
        String proposalHash,
        String contextHash,
        String itemKey,
        AigcGenerationProposalTargetType targetType,
        String targetKey,
        String actionKey,
        String generationInstruction,
        String instructionHash,
        List<String> dependencyKeys,
        List<String> requiredContextKeys,
        String modelPolicy,
        BigDecimal estimatedCredits,
        String confirmationPolicy,
        Integer itemVersion,
        String itemHash) {

    public AigcApprovedGenerationTarget {
        dependencyKeys = dependencyKeys == null ? List.of() : List.copyOf(dependencyKeys);
        requiredContextKeys =
                requiredContextKeys == null ? List.of() : List.copyOf(requiredContextKeys);
    }
}
