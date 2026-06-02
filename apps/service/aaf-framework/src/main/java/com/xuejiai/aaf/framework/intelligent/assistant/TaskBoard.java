package com.xuejiai.aaf.framework.intelligent.assistant;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 任务看板——会话级子任务状态追踪，支持依赖关系。
 *
 * <p>会话级，内存 + Checkpoint（长任务时持久化）。
 */
public class TaskBoard {

    /** 任务状态 */
    public enum TaskStatus {
        PENDING,
        RUNNING,
        DONE,
        FAILED
    }

    /** 子任务 */
    public record SubTask(
            String id,
            String description,
            TaskStatus status,
            List<String> dependsOn,
            String result) {

        public SubTask withStatus(TaskStatus newStatus) {
            return new SubTask(id, description, newStatus, dependsOn, result);
        }

        public SubTask withResult(String newResult) {
            return new SubTask(id, description, status, dependsOn, newResult);
        }
    }

    private final Map<String, SubTask> tasks = new ConcurrentHashMap<>();
    private final List<String> order = new CopyOnWriteArrayList<>();

    /** 添加子任务 */
    public void addTask(String id, String description, List<String> dependsOn) {
        tasks.put(
                id,
                new SubTask(
                        id,
                        description,
                        TaskStatus.PENDING,
                        dependsOn != null ? dependsOn : List.of(),
                        null));
        order.add(id);
    }

    /** 标记任务开始执行 */
    public void markRunning(String id) {
        updateStatus(id, TaskStatus.RUNNING);
    }

    /** 标记任务完成 */
    public void markDone(String id, String result) {
        tasks.computeIfPresent(id, (k, t) -> t.withStatus(TaskStatus.DONE).withResult(result));
    }

    /** 标记任务失败 */
    public void markFailed(String id, String reason) {
        tasks.computeIfPresent(id, (k, t) -> t.withStatus(TaskStatus.FAILED).withResult(reason));
    }

    /** 获取下一个可执行的任务（依赖已满足且状态为 PENDING） */
    public Optional<SubTask> nextReady() {
        return order.stream()
                .map(tasks::get)
                .filter(t -> t != null && t.status() == TaskStatus.PENDING)
                .filter(t -> t.dependsOn().stream().allMatch(this::isDone))
                .findFirst();
    }

    /** 所有任务是否已完成（DONE 或 FAILED） */
    public boolean isAllFinished() {
        return tasks.values().stream()
                .allMatch(t -> t.status() == TaskStatus.DONE || t.status() == TaskStatus.FAILED);
    }

    /** 是否有失败任务 */
    public boolean hasFailure() {
        return tasks.values().stream().anyMatch(t -> t.status() == TaskStatus.FAILED);
    }

    /** 获取所有任务（按添加顺序） */
    public List<SubTask> allTasks() {
        return order.stream().map(tasks::get).filter(t -> t != null).toList();
    }

    /** 获取指定任务 */
    public Optional<SubTask> getTask(String id) {
        return Optional.ofNullable(tasks.get(id));
    }

    /** 序列化为可持久化的快照 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> toSnapshot() {
        var snapshot = new java.util.HashMap<String, Object>();
        snapshot.put("order", List.copyOf(order));
        var taskList =
                allTasks().stream()
                        .map(
                                t ->
                                        Map.of(
                                                "id", t.id(),
                                                "description", t.description(),
                                                "status", t.status().name(),
                                                "dependsOn", t.dependsOn(),
                                                "result", t.result() != null ? t.result() : ""))
                        .toList();
        snapshot.put("tasks", taskList);
        return snapshot;
    }

    /** 从快照恢复 TaskBoard */
    @SuppressWarnings("unchecked")
    public static TaskBoard fromSnapshot(Map<String, Object> snapshot) {
        var board = new TaskBoard();
        var orderList = (List<String>) snapshot.get("order");
        var taskList = (List<Map<String, Object>>) snapshot.get("tasks");
        if (orderList == null || taskList == null) return board;

        for (var taskMap : taskList) {
            var id = (String) taskMap.get("id");
            var description = (String) taskMap.get("description");
            var status = TaskStatus.valueOf((String) taskMap.get("status"));
            var dependsOn = (List<String>) taskMap.get("dependsOn");
            var result = (String) taskMap.get("result");
            board.tasks.put(
                    id,
                    new SubTask(
                            id,
                            description,
                            status,
                            dependsOn != null ? dependsOn : List.of(),
                            result.isEmpty() ? null : result));
            board.order.add(id);
        }
        return board;
    }

    private boolean isDone(String taskId) {
        var task = tasks.get(taskId);
        return task != null && task.status() == TaskStatus.DONE;
    }

    private void updateStatus(String id, TaskStatus status) {
        tasks.computeIfPresent(id, (k, t) -> t.withStatus(status));
    }
}
