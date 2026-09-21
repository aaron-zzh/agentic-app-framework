package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

/** Execution/Task/Credit 向项目只读投影提供的聚合证据。 */
public interface AigcProjectProgressEvidencePort {

    Evidence load(Long projectId, Long userId);

    /**
     * 按已确认 Proposal 反查每个目标的 root Run。
     *
     * <p>关联依据是 root Run 冻结在 {@code effectiveInput.approvedGenerationTarget} 里的 Proposal 身份与
     * itemKey—— 唯一写入方是 Proposal→action 桥接，因此这里读到的就是执行侧真理源，不需要在项目域另建进度表。未开始的目标不会出现在结果中。
     */
    List<ApprovedTargetRunRef> approvedTargetRuns(
            Long projectId, String proposalId, Integer proposalVersion);

    /**
     * 按项目反查每个生成目标的最新 root Run，不限定 Proposal 版本。
     *
     * <p>供文档、初始化字段等实体在自己的界面上显示生成状态：这类状态属于实体本身而不属于某次任务，任务终结后仍需保留， 因此取全项目内该 itemKey
     * 最新的一次执行。从未生成过的目标不会出现在结果中。
     */
    List<ApprovedTargetRunRef> latestTargetRuns(Long projectId);

    /** 单个已确认目标当前的 root Run 状态。 */
    record ApprovedTargetRunRef(String itemKey, Long executionRunId, String status) {}

    record Evidence(
            List<ExecutionProgressRef> executions,
            ActualCreditSummary credits,
            java.util.Map<Long, ActualCreditSummary> creditsByObject) {
        public Evidence {
            executions = executions == null ? List.of() : List.copyOf(executions);
            creditsByObject =
                    creditsByObject == null
                            ? java.util.Map.of()
                            : java.util.Map.copyOf(creditsByObject);
        }
    }

    record ExecutionProgressRef(
            Long executionRunId,
            Long projectObjectId,
            String actionKey,
            String targetType,
            String status,
            List<TaskProgressRef> tasks,
            Long estimatedCredits,
            Long reservedCredits,
            String failureReason) {
        public ExecutionProgressRef {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
        }
    }

    record TaskProgressRef(
            Long taskId,
            Long executionRunId,
            String taskType,
            String status,
            Long mediaVersionId,
            Long creditTxId,
            String failureReason) {}

    record ActualCreditSummary(
            long estimated,
            long reserved,
            long settled,
            long refunded,
            long netActual,
            Long unknown,
            List<Long> unknownTaskIds) {
        public ActualCreditSummary {
            unknownTaskIds = unknownTaskIds == null ? List.of() : List.copyOf(unknownTaskIds);
        }
    }
}
