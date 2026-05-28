package com.xuejiai.aaf.framework.engine.checkpoint;

import java.time.Instant;
import java.util.Map;

/**
 * Checkpoint 条目——保存执行状态快照。
 *
 * @param id        检查点唯一 ID
 * @param ownerId   所属实体 ID（如 sessionId、executionId）
 * @param ownerType 所属实体类型
 * @param step      步骤序号
 * @param state     状态快照（可序列化的 Map）
 * @param createdAt 创建时间
 * @param expiresAt 过期时间
 */
public record CheckpointEntry(
        String id,
        String ownerId,
        OwnerType ownerType,
        int step,
        Map<String, Object> state,
        Instant createdAt,
        Instant expiresAt) {

    public enum OwnerType {
        AGENT, ASSISTANT, TEAM
    }
}
