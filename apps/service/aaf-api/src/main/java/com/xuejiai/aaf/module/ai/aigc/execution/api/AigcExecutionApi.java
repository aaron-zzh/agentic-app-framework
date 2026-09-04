package com.xuejiai.aaf.module.ai.aigc.execution.api;

public interface AigcExecutionApi {

    AigcExecutionRunView submit(AigcActionCommand command);

    AigcExecutionRunView submitDeferred(AigcActionCommand command);

    AigcExecutionRunView cancel(Long executionRunId, String reason);

    AigcExecutionRunView retry(Long executionRunId, String idempotencyKey);

    AigcExecutionRunView requireRun(Long executionRunId);

    AigcExecutionRunView latestProjectCoverRun(Long projectId);

    boolean isCurrentProjectCoverRun(Long projectId, Long executionRunId);

    void cancelProjectCoverRuns(Long projectId, String reason);

    void deleteProjectResources(Long projectId);
}
