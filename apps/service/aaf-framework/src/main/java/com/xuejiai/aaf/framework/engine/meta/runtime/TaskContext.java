package com.xuejiai.aaf.framework.engine.meta.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 任务执行上下文。
 *
 * <p>统一封装三种触发场景的执行数据：
 *
 * <ul>
 *   <li>定时任务：payload 为空，variables 含调度元信息
 *   <li>队列任务：payload 为消息 JSON，variables 含优先级、重试次数
 *   <li>请求任务：payload 为业务参数，含进度回调（{@code progressCallback}）
 * </ul>
 *
 * @author AaronZZH
 */
public class TaskContext {

    /** 任务 ID（executionId，由 TaskRuntime 分配） */
    private final String executionId;

    /** 任务类型 */
    private final String taskType;

    /** 输入 payload（JSON 字符串，可为空） */
    private final String payload;

    /** 流程变量（跨步骤共享状态） */
    private final Map<String, Object> variables = new ConcurrentHashMap<>();

    /** 进度回调（请求触发任务使用，定时/队列任务传 null） */
    private final Consumer<Integer> progressCallback;

    /** 当前进度（0-100） */
    private volatile int progress = 0;

    public TaskContext(
            String executionId,
            String taskType,
            String payload,
            Consumer<Integer> progressCallback) {
        this.executionId = executionId;
        this.taskType = taskType;
        this.payload = payload;
        this.progressCallback = progressCallback;
    }

    public TaskContext(String executionId, String taskType, String payload) {
        this(executionId, taskType, payload, null);
    }

    /** 更新进度（0-100），有回调时自动通知 */
    public void updateProgress(int progress) {
        this.progress = Math.min(100, Math.max(0, progress));
        if (progressCallback != null) {
            progressCallback.accept(this.progress);
        }
    }

    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        return (T) variables.get(key);
    }

    public String executionId() {
        return executionId;
    }

    public String taskType() {
        return taskType;
    }

    public String payload() {
        return payload;
    }

    public int progress() {
        return progress;
    }

    public Map<String, Object> variables() {
        return Map.copyOf(variables);
    }
}
