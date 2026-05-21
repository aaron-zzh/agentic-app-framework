package com.xuejiai.aaf.framework.task;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** 任务注册表，支持动态添加/暂停/恢复。 */
@Slf4j
@Component
public class TaskRegistry {

    private final Map<String, TaskDefinition> definitions = new ConcurrentHashMap<>();

    /** 注册任务定义 */
    public void register(TaskDefinition def) {
        definitions.put(def.name(), def);
        log.info("注册定时任务: {} [{}]", def.name(), def.cronExpression());
    }

    /** 暂停任务 */
    public void pause(String name) {
        definitions.computeIfPresent(
                name,
                (k, v) ->
                        new TaskDefinition(
                                v.name(),
                                v.cronExpression(),
                                v.taskClass(),
                                false,
                                v.description()));
        log.info("暂停定时任务: {}", name);
    }

    /** 恢复任务 */
    public void resume(String name) {
        definitions.computeIfPresent(
                name,
                (k, v) ->
                        new TaskDefinition(
                                v.name(),
                                v.cronExpression(),
                                v.taskClass(),
                                true,
                                v.description()));
        log.info("恢复定时任务: {}", name);
    }

    /** 获取所有任务定义 */
    public List<TaskDefinition> listAll() {
        return List.copyOf(definitions.values());
    }

    /** 获取指定任务定义 */
    public TaskDefinition get(String name) {
        return definitions.get(name);
    }

    public boolean contains(String name) {
        return definitions.containsKey(name);
    }
}
