package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 创建待确认项目生成 Proposal 的类型化输入；不触发 Task、Run 或 candidate。 */
public record AigcProjectGenerationProposalDraft(
        AigcGenerationProposalScope scope,
        Integer expectedProjectVersion,
        String sourceType,
        String sourceRef,
        String confirmationPolicy,
        BigDecimal estimatedCredits,
        LocalDateTime expiresAt,
        List<Item> items) {

    public AigcProjectGenerationProposalDraft {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 单一生成目标的已提议指令与冻结输入引用。 */
    public record Item(
            AigcGenerationProposalTargetType targetType,
            String targetKey,
            String actionKey,
            String generationInstruction,
            List<String> dependencyKeys,
            List<String> requiredContextKeys,
            String modelPolicy,
            BigDecimal estimatedCredits,
            String confirmationPolicy) {

        public Item {
            dependencyKeys = dependencyKeys == null ? List.of() : List.copyOf(dependencyKeys);
            requiredContextKeys =
                    requiredContextKeys == null ? List.of() : List.copyOf(requiredContextKeys);
        }
    }
}
