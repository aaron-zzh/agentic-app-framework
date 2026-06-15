package com.xuejiai.aaf.framework.engine.task;

import java.util.Optional;

/**
 * 通用检查点存储接口——支持长任务断点恢复。
 *
 * <p>业务层（如 module.ai.chat）提供 JPA 实现； framework 层通过此接口操作，不感知具体存储细节。
 *
 * @author Kiro
 */
public interface CheckpointStore {

    /**
     * 保存检查点（追加写入，不覆盖）。
     *
     * @param executionId 执行实例 ID
     * @param scope 检查点作用域（如 coordinator / agent_step）
     * @param stepIndex 步骤序号
     * @param stateJson 状态快照 JSON
     */
    void save(Long executionId, String scope, int stepIndex, String stateJson);

    /**
     * 加载最新检查点。
     *
     * @param executionId 执行实例 ID
     * @return 最新检查点状态 JSON，无记录返回 empty
     */
    Optional<String> loadLatest(Long executionId);
}
