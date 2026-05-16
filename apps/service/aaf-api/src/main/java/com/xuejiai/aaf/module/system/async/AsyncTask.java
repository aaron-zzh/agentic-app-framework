package com.xuejiai.aaf.module.system.async;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 异步任务模型。内存存储，轻量级进度追踪。
 */
@Data
public class AsyncTask {

    private String taskId;
    private String type;
    private Status status = Status.PENDING;
    private int current;
    private int total;
    private String result;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime completeTime;

    public enum Status {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    public int getPercentage() {
        return total > 0 ? (current * 100 / total) : 0;
    }
}
