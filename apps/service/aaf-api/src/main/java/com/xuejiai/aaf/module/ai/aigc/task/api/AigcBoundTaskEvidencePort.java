package com.xuejiai.aaf.module.ai.aigc.task.api;

/** 项目 Task 创建前必须验证的 execution BOUND 证据端口。 */
public interface AigcBoundTaskEvidencePort {

    BoundEvidence requireBound(Long executionRunId, Long projectId, Long projectObjectId);

    record BoundEvidence(
            Long ownerId,
            Long orgId,
            Long workspaceId,
            Long executionSubmissionId,
            Long executionReservationId,
            Long rootExecutionRunId) {}
}
