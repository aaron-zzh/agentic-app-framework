package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

/**
 * 单一原子生成启动命令：结构化确认 Proposal → 创建 canonical Task → 成功后关联 Task。
 *
 * <p>客户端只提交作用域、Proposal 身份、版本、hash 与确认范围；taskId 由服务端按作用域身份 + `proposalId + proposalVersion +
 * proposalHash` 确定性派生，重放返回同一 Task。禁止拆成 approve / create / link 三次客户端调用。
 *
 * <p>{@code assistantId} 仅在 {@code CREATION_DRAFT} 作用域必填（此时还没有项目受控会话，需显式选择 Assistant）；
 * 项目作用域一律使用服务端受控项目会话，提交该字段即拒绝。
 */
public record AigcProjectGenerationStartCommand(
        AigcGenerationProposalScope scope,
        Integer expectedProjectVersion,
        String proposalId,
        Integer proposalVersion,
        String proposalHash,
        List<String> approvedTargetKeys,
        String approvalIdempotencyKey,
        String assistantId) {

    public AigcProjectGenerationStartCommand {
        approvedTargetKeys =
                approvedTargetKeys == null ? List.of() : List.copyOf(approvedTargetKeys);
    }
}
