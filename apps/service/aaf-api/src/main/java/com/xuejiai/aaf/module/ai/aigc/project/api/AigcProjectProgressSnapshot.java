package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AigcProjectProgressSnapshot(
        int projectionVersion,
        Instant updatedAt,
        Long projectId,
        Integer projectVersion,
        Long graphRevision,
        CurrentTaskRef currentTask,
        List<ObjectProgressRef> objects,
        List<AigcObjectReadiness> readiness,
        List<AigcProjectProgressEvidencePort.ExecutionProgressRef> executions,
        List<CandidateDecisionRef> candidates,
        DeliveryProgress delivery,
        AigcProjectProgressEvidencePort.ActualCreditSummary credits,
        /**
         * 项目初始化字段最近一次生成执行的状态，键是字段键（如 {@code background}）。
         *
         * <p>初始化字段是项目自身的列，没有独立的实体投影，因此状态随项目进度快照返回；项目对象的状态在 readiness 里， 文档的状态在文档引用投影里，都不重复放进这里。与
         * {@link CurrentTaskRef#targetRuns()} 的区别是不限定当前任务， 任务终结后仍保留。
         */
        Map<String, String> initializationFieldGenerationStatus) {

    public AigcProjectProgressSnapshot {
        objects = objects == null ? List.of() : List.copyOf(objects);
        readiness = readiness == null ? List.of() : List.copyOf(readiness);
        executions = executions == null ? List.of() : List.copyOf(executions);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        initializationFieldGenerationStatus =
                initializationFieldGenerationStatus == null
                        ? Map.of()
                        : Map.copyOf(initializationFieldGenerationStatus);
    }

    /**
     * 项目当前未终结的 canonical Task 投影。
     *
     * <p>状态真理源是 canonical Task；本投影只做只读展示与入口跳转，不复制任务状态机。没有未终结 Task 时整体为 {@code null}， 其余进度区段仍必须完整可用。
     */
    public record CurrentTaskRef(
            String taskId,
            String status,
            String proposalId,
            Integer proposalVersion,
            List<String> itemKeys,
            List<AigcProjectProgressEvidencePort.ApprovedTargetRunRef> targetRuns,
            List<String> decisionBlockers) {

        public CurrentTaskRef {
            itemKeys = itemKeys == null ? List.of() : List.copyOf(itemKeys);
            targetRuns = targetRuns == null ? List.of() : List.copyOf(targetRuns);
            decisionBlockers = decisionBlockers == null ? List.of() : List.copyOf(decisionBlockers);
        }
    }

    public record ObjectProgressRef(
            Long objectId,
            String maturity,
            String contractRole,
            AigcObjectReadinessState readiness,
            AigcProjectProgressEvidencePort.ActualCreditSummary credits) {}

    public record CandidateDecisionRef(
            Long candidateVersionId,
            Long objectId,
            String status,
            Long executionRunId,
            Long documentVersionId,
            List<Long> mediaVersionIds) {
        public CandidateDecisionRef {
            mediaVersionIds = mediaVersionIds == null ? List.of() : List.copyOf(mediaVersionIds);
        }
    }

    public record DeliveryProgress(
            boolean completionSatisfied,
            int reviewCount,
            long pendingReviewCount,
            long activeWorkCount,
            long publicationCount,
            List<String> blockers) {
        public DeliveryProgress {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }
}
