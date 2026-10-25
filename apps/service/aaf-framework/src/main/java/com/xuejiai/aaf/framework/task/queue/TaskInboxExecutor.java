package com.xuejiai.aaf.framework.task.queue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 队列任务 inbox 执行器：短事务状态变更，handler 在事务外运行。 */
@Component
@RequiredArgsConstructor
public class TaskInboxExecutor {

    private final TaskInboxStateService inboxStateService;

    /** 领取任务后在事务外执行 handler，成功后完成 inbox；失败则释放租约供队列重试。 */
    public String execute(
            String taskId, String taskType, String payload, Supplier<String> handler) {
        Objects.requireNonNull(handler, "handler 不能为空");
        var normalizedTaskId = requireText(taskId, "taskId");
        var normalizedTaskType = requireText(taskType, "taskType");
        var payloadDigest = digest(Objects.requireNonNullElse(payload, ""));
        var claim = inboxStateService.claim(normalizedTaskId, normalizedTaskType, payloadDigest);
        if (claim.completed()) {
            return claim.result();
        }
        try {
            var result = handler.get();
            inboxStateService.complete(claim, result);
            return result;
        } catch (RuntimeException | Error failure) {
            inboxStateService.release(claim);
            throw failure;
        }
    }

    private String digest(String payload) {
        try {
            var bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    private String requireText(String value, String field) {
        var normalized = Objects.requireNonNull(value, field + " 不能为空").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return normalized;
    }
}
