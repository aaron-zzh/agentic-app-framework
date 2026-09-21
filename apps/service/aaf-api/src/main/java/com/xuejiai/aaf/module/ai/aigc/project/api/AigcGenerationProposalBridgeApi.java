package com.xuejiai.aaf.module.ai.aigc.project.api;

/**
 * Proposal→action 桥接端口。
 *
 * <p>只允许服务端受信代码按 canonical Task 身份反查已确认目标：taskId 由 {@code InvocationContext} 提供，模型与客户端都不能声明
 * proposalId、版本、hash 或目标范围。返回值必须整体冻结进 root {@code AigcExecutionRun}，不得拆分后重新组装。
 */
public interface AigcGenerationProposalBridgeApi {

    /**
     * 按 canonical Task 反查该 Task 内的某个已确认生成目标，并重新校验当前项目上下文。
     *
     * @param linkedTaskId canonical Task 身份，必须已由 approve-and-start 写入 Proposal
     * @param trustedProjectId 执行画像冻结的受信 projectId，必须与 Proposal 项目一致
     * @param itemKey 目标 key，必须落在该 Proposal 的已批准范围内——Assistant 只决定推进顺序，不能扩大范围
     */
    AigcApprovedGenerationTarget resolveApprovedTargetByLinkedTask(
            String linkedTaskId, Long trustedProjectId, String itemKey);

    /**
     * 返回该项目已关联 canonical Task 的 Proposal 身份，供调用方按 Task 真理源判断是否仍有未终结任务。
     *
     * @param excludedProposalId 允许排除的 proposalId（同一 Proposal 的幂等重放不算竞争任务）
     */
    java.util.List<LinkedTask> linkedTasks(Long projectId, String excludedProposalId);

    /** 项目生成 Proposal 与其 canonical Task 的关联投影；状态仍由 canonical Task 决定。 */
    record LinkedTask(
            String proposalId,
            Integer proposalVersion,
            String taskId,
            java.util.List<String> itemKeys) {

        public LinkedTask {
            itemKeys = itemKeys == null ? java.util.List.of() : java.util.List.copyOf(itemKeys);
        }
    }
}
