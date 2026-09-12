package com.xuejiai.aaf.framework.intelligent.assistant.model.analysis;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent.InteractionMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent.PersistenceMode;

/** DIRECT/TASK/TEAM 的确定性硬门；复杂度与分解仍由当前 Task Owner Assistant 负责。 */
public final class TaskAnalysisPolicy {

    private TaskAnalysisPolicy() {}

    public static TaskAnalysis analyze(ExecutionIntent intent, boolean teamRequested) {
        Objects.requireNonNull(intent, "intent 不能为空");
        if (teamRequested) {
            return new TaskAnalysis(TaskAnalysis.Route.TEAM, "请求绑定已发布 Team");
        }
        if (intent.interactionMode() == InteractionMode.TASK) {
            return new TaskAnalysis(TaskAnalysis.Route.TASK, "请求显式要求持久任务执行");
        }
        if (intent.artifactPolicy().persistenceMode() != PersistenceMode.RETURN_ONLY) {
            return new TaskAnalysis(TaskAnalysis.Route.TASK, "请求包含持久产物或副作用边界");
        }
        return new TaskAnalysis(TaskAnalysis.Route.DIRECT, "普通 conversational return-only 消息");
    }
}
