package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

/** 对指定 Proposal 版本和哈希的结构化确认；自然语言确认也必须先归约为本合同。 */
public record AigcProjectGenerationProposalApprovalCommand(
        AigcGenerationProposalScope scope,
        String proposalId,
        Integer proposalVersion,
        String proposalHash,
        Integer expectedProjectVersion,
        List<String> approvedTargetKeys,
        String idempotencyKey) {

    public AigcProjectGenerationProposalApprovalCommand {
        approvedTargetKeys =
                approvedTargetKeys == null ? List.of() : List.copyOf(approvedTargetKeys);
    }
}
