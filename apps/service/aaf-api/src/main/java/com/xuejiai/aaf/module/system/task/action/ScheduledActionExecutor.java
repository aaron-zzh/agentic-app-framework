package com.xuejiai.aaf.module.system.task.action;

import com.xuejiai.aaf.module.system.task.domain.ScheduledTask;

/** 计划任务动作执行器——每种 actionType 对应一个实现。 */
public interface ScheduledActionExecutor {

    /** 支持的动作类型 */
    String actionType();

    /** 执行动作 */
    void execute(ScheduledTask task);
}
