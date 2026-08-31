package com.xuejiai.aaf.framework.intelligent.agent.model;

import java.time.Duration;
import java.util.Objects;

/**
 * 单次 Agent 回合的确定性执行边界。
 *
 * <p>{@code contextWindow} 是本次执行所用模型的上下文窗口 token 数，唯一来源是 {@code
 * aaf.ai.context.default-context-window} 配置，不在此处保留第二份默认值。它让调用前长度预检能算出占用率，而不只是绝对长度。
 *
 * <p>三个时限各自表达一种边界，禁止用一个操作符混合表达（RQ-03）：
 *
 * <ul>
 *   <li>{@code totalTimeout} —— 整次执行的墙钟硬时限，从订阅开始计时，覆盖模型调用、工具调用与事件入库全过程
 *   <li>{@code idleTimeout} —— 相邻两个已映射事件之间的静默上限，用于识别"模型卡住但连接未断"
 *   <li>{@code persistTimeout} —— 单条事件入库的写入 SLA，与模型侧时限完全解耦
 * </ul>
 */
public record ExecutionPolicy(
        int maxIterations,
        int maxModelRetries,
        Duration totalTimeout,
        Duration idleTimeout,
        Duration persistTimeout,
        int contextWindow) {

    /** 事件静默默认上限：模型侧连续 2 分钟无已映射事件即视为卡死。 */
    public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofSeconds(120);

    /** 单条事件入库默认 SLA：数据库写入超过 10 秒即视为存储侧故障。 */
    public static final Duration DEFAULT_PERSIST_TIMEOUT = Duration.ofSeconds(10);

    public ExecutionPolicy {
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations 必须大于 0");
        }
        if (maxModelRetries < 0) {
            throw new IllegalArgumentException("maxModelRetries 不能小于 0");
        }
        requirePositive(totalTimeout, "totalTimeout");
        requirePositive(idleTimeout, "idleTimeout");
        requirePositive(persistTimeout, "persistTimeout");
        if (idleTimeout.compareTo(totalTimeout) > 0) {
            throw new IllegalArgumentException("idleTimeout 不能大于 totalTimeout");
        }
        if (persistTimeout.compareTo(totalTimeout) > 0) {
            throw new IllegalArgumentException("persistTimeout 不能大于 totalTimeout");
        }
        if (contextWindow < 1) {
            throw new IllegalArgumentException("contextWindow 必须大于 0");
        }
    }

    /** 只给出总时限时按框架默认值补齐静默与入库时限；静默时限不得超过总时限。 */
    public static ExecutionPolicy withDefaultTimeouts(
            int maxIterations, int maxModelRetries, Duration totalTimeout, int contextWindow) {
        requirePositive(totalTimeout, "totalTimeout");
        return new ExecutionPolicy(
                maxIterations,
                maxModelRetries,
                totalTimeout,
                min(DEFAULT_IDLE_TIMEOUT, totalTimeout),
                min(DEFAULT_PERSIST_TIMEOUT, totalTimeout),
                contextWindow);
    }

    private static Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static void requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " 必须大于 0");
        }
    }
}
