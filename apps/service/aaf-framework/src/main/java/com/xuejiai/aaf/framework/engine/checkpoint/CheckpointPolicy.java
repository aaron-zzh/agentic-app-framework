package com.xuejiai.aaf.framework.engine.checkpoint;

import java.time.Duration;

/** Checkpoint 策略枚举。 */
public enum CheckpointPolicy {
    /** 每步保存 */
    EVERY_STEP,
    /** 每 N 步保存 */
    EVERY_N_STEPS,
    /** 仅关键节点保存 */
    KEY_NODES_ONLY;

    /**
     * Checkpoint 配置。
     *
     * @param policy 策略
     * @param stepInterval 步骤间隔（EVERY_N_STEPS 时生效）
     * @param ttl 检查点过期时间
     */
    public record CheckpointConfig(CheckpointPolicy policy, int stepInterval, Duration ttl) {

        public static CheckpointConfig defaults() {
            return new CheckpointConfig(EVERY_N_STEPS, 3, Duration.ofHours(24));
        }
    }
}
