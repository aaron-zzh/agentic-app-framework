package com.xuejiai.aaf.module.ai.aigc.execution.api;

public interface AigcExecutionApi {

    AigcExecutionRunView submit(AigcActionCommand command);

    AigcExecutionRunView cancel(Long executionRunId, String reason);

    AigcExecutionRunView retry(Long executionRunId, String idempotencyKey);

    AigcExecutionRunView requireRun(Long executionRunId);

    void deleteProjectResources(Long projectId);
}
