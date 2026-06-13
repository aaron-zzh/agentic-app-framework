package com.xuejiai.aaf.module.system.task.service;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户自定义计划任务的通用 Runnable。
 *
 * <p>prototype 作用域——每次由 {@code BeanFactory.getBean()} 创建新实例， 调用方通过 setter 注入 {@code taskService} 和
 * {@code taskId} 后执行。
 *
 * <p>执行逻辑：从 DB 按 taskId 读取 {@code actionType/actionConfig}， 委托给对应的 {@link ScheduledActionExecutor}
 * 实现。
 */
@Slf4j
@Component
@Scope("prototype")
public class UserDefinedTaskRunnable implements Runnable {

    /** 由调用方注入，用于查询任务和执行动作 */
    @Setter private ScheduledTaskService taskService;

    /** DB 中的 sys_scheduled_task.id */
    @Setter private Long taskId;

    @Override
    public void run() {
        if (taskService == null || taskId == null) {
            log.warn(
                    "UserDefinedTaskRunnable 未正确初始化（taskService={}, taskId={}），跳过执行",
                    taskService,
                    taskId);
            return;
        }
        var task = taskService.getById(taskId);
        taskService.executeAction(task);
    }
}
