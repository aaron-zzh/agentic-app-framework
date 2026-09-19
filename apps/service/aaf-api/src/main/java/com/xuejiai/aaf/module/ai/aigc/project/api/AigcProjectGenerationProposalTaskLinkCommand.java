package com.xuejiai.aaf.module.ai.aigc.project.api;

/** 已确认 Proposal 与其唯一 canonical Task 的原子关联命令。 */
public record AigcProjectGenerationProposalTaskLinkCommand(
        AigcGenerationProposalScope scope,
        String proposalId,
        Integer proposalVersion,
        String proposalHash,
        Integer expectedProjectVersion,
        String linkedTaskId) {}
