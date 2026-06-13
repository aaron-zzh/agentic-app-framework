package com.xuejiai.aaf.framework.task;

import java.time.LocalDateTime;
import java.util.List;

/** 任务持久化端口——framework 层定义接口，api 层提供实现。 解耦 framework 对业务模块的依赖。 */
public interface TaskPersistencePort {

    /** 加载所有需要调度的持久化任务定义 */
    List<TaskDefinition> loadActiveTasks();

    /** 查询任务最后成功执行时间（用于 misfire 检查），无记录返回 null */
    LocalDateTime getLastRun(String taskName);

    /** 更新任务最后执行时间 */
    void updateLastRun(String taskName, LocalDateTime lastRun);

    /** 记录任务失败，超阈值时暂停 */
    void recordFailure(String taskName, String errorMsg);
}
