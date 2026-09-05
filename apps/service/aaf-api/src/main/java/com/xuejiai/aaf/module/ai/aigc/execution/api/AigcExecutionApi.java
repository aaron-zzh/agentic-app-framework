package com.xuejiai.aaf.module.ai.aigc.execution.api;

public interface AigcExecutionApi {

    AigcExecutionRunView submit(AigcActionCommand command);

    AigcExecutionRunView submitDeferred(AigcActionCommand command);

    AigcExecutionRunView submitChild(AigcChildActionCommand command);

    AigcExecutionRunTreeView requireRunTree(Long rootExecutionRunId);

    AigcExecutionRunView cancel(Long executionRunId, String reason);

    AigcExecutionRunView retry(Long executionRunId, String idempotencyKey);

    AigcExecutionRunView requireRun(Long executionRunId);

    void cancelProjectCoverRuns(Long projectId, String reason);

}
