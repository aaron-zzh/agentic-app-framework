package com.xuejiai.aaf.module.ai.aigc.task.api;

import java.util.List;

public interface AigcTaskApi {

    AigcTaskView submit(AigcTaskSubmitCommand command);

    AigcTaskView cancel(Long taskId, String reason);

    AigcTaskView requireTask(Long taskId);

    List<AigcTaskProgressView> findProjectProgress(Long projectId, Long userId);
}
