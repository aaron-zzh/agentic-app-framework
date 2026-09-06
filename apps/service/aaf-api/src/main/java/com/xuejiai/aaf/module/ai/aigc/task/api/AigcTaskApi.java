package com.xuejiai.aaf.module.ai.aigc.task.api;

public interface AigcTaskApi {

    AigcTaskView submit(AigcTaskSubmitCommand command);

    AigcTaskView cancel(Long taskId, String reason);

    AigcTaskView requireTask(Long taskId);
}
