package com.xuejiai.aaf.framework.intelligent.assistant;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

/**
 * 子任务上下文——fork 时创建，任务完成后销毁。
 *
 * <p>关联 Checkpoint 支持断点恢复。
 */
@Getter
public class SubTaskContext {

    public enum Status {
        RUNNING, DONE, FAILED
    }

    private final String taskId;
    private final String parentSessionId;
    private final String role;
    private final String input;
    private Status status;
    private String result;
    private final Instant startTime;
    private String checkpointId;

    public SubTaskContext(String taskId, String parentSessionId, String role, String input) {
        this.taskId = taskId;
        this.parentSessionId = parentSessionId;
        this.role = role;
        this.input = input;
        this.status = Status.RUNNING;
        this.startTime = Instant.now();
    }

    /** 标记任务完成 */
    public void complete(String result) {
        this.status = Status.DONE;
        this.result = result;
    }

    /** 标记任务失败 */
    public void fail(String reason) {
        this.status = Status.FAILED;
        this.result = reason;
    }

    /** 关联 Checkpoint */
    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    /** 序列化为可持久化的 Map（用于 Checkpoint） */
    public Map<String, Object> toCheckpointState() {
        var state = new HashMap<String, Object>();
        state.put("taskId", taskId);
        state.put("parentSessionId", parentSessionId);
        state.put("role", role);
        state.put("input", input);
        state.put("status", status.name());
        state.put("result", result);
        state.put("startTime", startTime.toString());
        state.put("checkpointId", checkpointId);
        return state;
    }

    /** 从 Checkpoint 状态恢复 */
    public static SubTaskContext fromCheckpointState(Map<String, Object> state) {
        var ctx = new SubTaskContext(
                (String) state.get("taskId"),
                (String) state.get("parentSessionId"),
                (String) state.get("role"),
                (String) state.get("input"));
        ctx.status = Status.valueOf((String) state.get("status"));
        ctx.result = (String) state.get("result");
        ctx.checkpointId = (String) state.get("checkpointId");
        return ctx;
    }
}
