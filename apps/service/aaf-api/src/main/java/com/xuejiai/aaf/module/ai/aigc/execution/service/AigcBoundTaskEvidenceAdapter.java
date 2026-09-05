package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionSubmissionRepository;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcBoundTaskEvidencePort;

import lombok.RequiredArgsConstructor;

/** 从 execution 真理源验证项目 Task 的 BOUND 证据。 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcBoundTaskEvidenceAdapter implements AigcBoundTaskEvidencePort {

    private final AigcExecutionRunRepository runRepository;
    private final AigcExecutionSubmissionRepository submissionRepository;

    @Override
    public BoundEvidence requireBound(
            Long executionRunId, Long projectId, Long projectObjectId) {
        var run =
                runRepository
                        .findById(executionRunId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "执行 Run 不存在"));
        var submission =
                submissionRepository
                        .findById(run.getExecutionSubmissionId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "执行 submission 不存在"));
        if (!"BOUND".equals(submission.getStatus())
                || !Objects.equals(run.getProjectId(), projectId)
                || !Objects.equals(run.getObjectId(), projectObjectId)
                || !Objects.equals(submission.getProjectId(), projectId)
                || !Objects.equals(
                        submission.getRootExecutionRunId(), run.getRootExecutionRunId())
                || !Objects.equals(
                        submission.getReservationId(), run.getExecutionReservationId())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "项目 Task 缺少 BOUND execution 证据");
        }
        return new BoundEvidence(
                run.getOwnerId(),
                run.getOrgId(),
                run.getWorkspaceId(),
                submission.getId(),
                run.getExecutionReservationId(),
                run.getRootExecutionRunId());
    }
}
