package com.xuejiai.aaf.framework.engine.meta.runtime;

/**
 * 任务执行结果。
 *
 * @author AaronZZH
 */
public record TaskResult(boolean success, String output, String error) {

    public static TaskResult ok() {
        return new TaskResult(true, null, null);
    }

    public static TaskResult ok(String output) {
        return new TaskResult(true, output, null);
    }

    public static TaskResult fail(String error) {
        return new TaskResult(false, null, error);
    }

    public static TaskResult fail(Throwable e) {
        return new TaskResult(false, null, e.getMessage());
    }
}
