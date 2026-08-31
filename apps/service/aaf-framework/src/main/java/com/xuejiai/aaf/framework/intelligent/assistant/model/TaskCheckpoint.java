/**
 * 类型化任务检查点。
 *
 * @author Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Map;
import java.util.Objects;

/**
 * 委托任务的通用检查点，替代原无结构 {@code Map<String, Object>}（方案 C，2026-08-29 拍板）。
 *
 * <p>{@code version}/{@code contentHash}/{@code eventOffset} 是恢复一致性校验所需的显式字段； {@code annotations}
 * 保留原有的自由文本注解用法（如 {@code pauseReason}、{@code lastFailure} 等事实标注），不破坏既有调用点写入习惯。整体仍序列化进同一个 {@code
 * ai_delegated_task.task_payload} JSONB 列，不新增表或列。
 */
public record TaskCheckpoint(
        long version, String contentHash, Long eventOffset, Map<String, Object> annotations) {

    public TaskCheckpoint {
        if (version < 0) {
            throw new IllegalArgumentException("version 不能为负数");
        }
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }

    /** 空检查点：既未冻结版本，也没有任何注解。 */
    public static TaskCheckpoint empty() {
        return new TaskCheckpoint(0, null, null, Map.of());
    }

    /** 版本、hash 与 eventOffset 不变，只追加或覆盖一条注解，用于兼容既有的“事实标注”写法。 */
    public TaskCheckpoint withAnnotation(String key, Object value) {
        Objects.requireNonNull(key, "key 不能为空");
        var merged = new java.util.LinkedHashMap<>(annotations);
        merged.put(key, value == null ? "" : value);
        return new TaskCheckpoint(version, contentHash, eventOffset, Map.copyOf(merged));
    }

    /** 版本、hash 与 eventOffset 不变，批量合并注解；用于承载既有“借用 checkpoint 存放业务结果”的写法。 */
    public TaskCheckpoint withAnnotations(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        var merged = new java.util.LinkedHashMap<>(annotations);
        merged.putAll(values);
        return new TaskCheckpoint(version, contentHash, eventOffset, Map.copyOf(merged));
    }

    /** 推进到下一版本并写入新的一致性校验字段；用于真正的恢复安全边界写入，而非事实标注。 */
    public TaskCheckpoint advance(String contentHash, Long eventOffset) {
        return new TaskCheckpoint(version + 1, contentHash, eventOffset, annotations);
    }
}
