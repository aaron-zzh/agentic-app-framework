package com.xuejiai.aaf.framework.task.queue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** 任务处理器注册表，根据 taskType 路由到对应 handler。 */
@Slf4j
@Component
public class TaskHandlerRegistry {

    private final Map<String, TaskHandler> handlers;

    public TaskHandlerRegistry(List<TaskHandler> handlerList) {
        this.handlers = handlerList.stream().collect(Collectors.toMap(TaskHandler::taskType, Function.identity()));
        log.info("注册任务处理器: {}", handlers.keySet());
    }

    /** 根据任务类型获取处理器 */
    public TaskHandler getHandler(String taskType) {
        return handlers.get(taskType);
    }
}
