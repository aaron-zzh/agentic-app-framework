package com.xuejiai.aaf.module.ai.aigc.project.api;

/** Project 摘要读取执行态所需的跨子域证据端口。 */
public interface ExecutionEvidencePort {

    ExecutionEvidence load(Long projectId);

    record ExecutionEvidence(long runningCount) {

        public ExecutionEvidence {
            if (runningCount < 0) {
                throw new IllegalArgumentException("ExecutionEvidence 计数无效");
            }
        }
    }
}
