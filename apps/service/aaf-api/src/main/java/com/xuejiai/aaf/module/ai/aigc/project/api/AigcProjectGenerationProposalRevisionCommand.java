package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 修改生成目标指令时创建 Proposal 新版本，旧 hash 与确认随即失效。 */
public record AigcProjectGenerationProposalRevisionCommand(
        AigcGenerationProposalScope scope,
        String proposalId,
        Integer proposalVersion,
        String proposalHash,
        Integer expectedProjectVersion,
        String sourceRef,
        String confirmationPolicy,
        BigDecimal estimatedCredits,
        LocalDateTime expiresAt,
        List<AigcProjectGenerationProposalDraft.Item> items) {

    public AigcProjectGenerationProposalRevisionCommand {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
