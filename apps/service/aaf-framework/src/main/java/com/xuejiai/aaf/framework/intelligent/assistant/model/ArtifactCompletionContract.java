package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 专业 Tool durable accepted 后冻结的资源完成合同。 */
public final class ArtifactCompletionContract {

    private ArtifactCompletionContract() {}

    public enum OutputContract {
        INLINE_OBJECT_VERSION,
        DOCUMENT_VERSION,
        MEDIA_VERSION,
        COMPOSITE_MANIFEST,
        REFERENCE_ONLY
    }

    public enum PartialFailurePolicy {
        FAIL_ALL,
        ACCEPT_COMPLETED,
        PAUSE_FOR_DECISION
    }

    public record CompletionCriteria(
            Map<Long, OutputContract> expectedObjects, PartialFailurePolicy failurePolicy) {
        public CompletionCriteria {
            expectedObjects = Map.copyOf(expectedObjects);
            Objects.requireNonNull(failurePolicy, "failurePolicy 不能为空");
            if (expectedObjects.isEmpty()) {
                throw new IllegalArgumentException("expectedObjects 不能为空");
            }
        }
    }

    public record InvocationReceipt(
            TenantId tenantId,
            Long projectId,
            TaskId taskId,
            String planId,
            int planRevision,
            ExecutionId executionId,
            String toolCallId,
            List<Long> executionRunIds,
            List<Long> inputVersionIds,
            CompletionCriteria completionCriteria,
            long dispatchFencingToken) {
        public InvocationReceipt {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(projectId, "projectId 不能为空");
            Objects.requireNonNull(taskId, "taskId 不能为空");
            Objects.requireNonNull(executionId, "executionId 不能为空");
            if (projectId < 1 || dispatchFencingToken < 1) {
                throw new IllegalArgumentException("projectId 与 dispatchFencingToken 必须为正数");
            }
            executionRunIds = List.copyOf(executionRunIds);
            inputVersionIds = List.copyOf(inputVersionIds);
            Objects.requireNonNull(completionCriteria, "completionCriteria 不能为空");
            if (planId == null || planId.isBlank() || planRevision < 1) {
                throw new IllegalArgumentException("plan identity 不完整");
            }
            if (toolCallId == null || toolCallId.isBlank() || executionRunIds.isEmpty()) {
                throw new IllegalArgumentException("Tool receipt identity 不完整");
            }
        }
    }

    public interface ArtifactCompletionEvaluator {
        Evaluation evaluate(InvocationReceipt receipt);
    }

    /** 外部权威状态变化信号；不携带完成结论，由恢复入口重新查询。 */
    public record CompletionSignal(Long executionRunId) {
        public CompletionSignal {
            Objects.requireNonNull(executionRunId, "executionRunId 不能为空");
            if (executionRunId < 1) {
                throw new IllegalArgumentException("executionRunId 必须为正数");
            }
        }
    }

    public record Evaluation(boolean completed, boolean terminalFailure, List<String> blockers) {
        public Evaluation {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            if (completed && terminalFailure) {
                throw new IllegalArgumentException("completed 与 terminalFailure 不能同时为 true");
            }
        }
    }
}
