package com.xuejiai.aaf.module.ai.aigc.execution.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.project.api.ExecutionEvidencePort;

import lombok.RequiredArgsConstructor;

/** 从 ExecutionRun 真理源投影项目运行态证据。 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcProjectExecutionEvidenceAdapter implements ExecutionEvidencePort {

    private final AigcExecutionRunRepository repository;

    @Override
    public ExecutionEvidence load(Long projectId) {
        return new ExecutionEvidence(
                repository.countByProjectIdAndStatus(
                        projectId, AigcExecutionRunStatus.RUNNING));
    }
}
