package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 项目生成 Proposal 的只读投影；不包含最终 Prompt 正文。 */
public record AigcProjectGenerationProposalView(
        AigcGenerationProposalScope.Kind scopeKind,
        Long projectId,
        String draftId,
        Long blueprintVersionId,
        Long linkedProjectId,
        String proposalId,
        Integer proposalVersion,
        String proposalHash,
        String contextHash,
        String status,
        String confirmationPolicy,
        BigDecimal estimatedCredits,
        List<String> approvedTargetKeys,
        String linkedTaskId,
        LocalDateTime expiresAt,
        List<Item> items) {

    public AigcProjectGenerationProposalView {
        approvedTargetKeys =
                approvedTargetKeys == null ? List.of() : List.copyOf(approvedTargetKeys);
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 单项目标的提议内容与可审计 hash。 */
    public record Item(
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

        public Item {
            dependencyKeys = dependencyKeys == null ? List.of() : List.copyOf(dependencyKeys);
            requiredContextKeys =
                    requiredContextKeys == null ? List.of() : List.copyOf(requiredContextKeys);
        }
    }
}
