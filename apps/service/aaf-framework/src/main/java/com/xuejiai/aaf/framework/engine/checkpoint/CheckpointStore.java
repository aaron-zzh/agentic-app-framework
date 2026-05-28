package com.xuejiai.aaf.framework.engine.checkpoint;

import java.util.List;
import java.util.Optional;

/**
 * Checkpoint 存储接口——通用检查点持久化。
 */
public interface CheckpointStore {

    /** 保存检查点 */
    void save(CheckpointEntry entry);

    /** 加载指定检查点 */
    Optional<CheckpointEntry> load(String checkpointId);

    /** 删除指定检查点 */
    void delete(String checkpointId);

    /** 列出指定 owner 的所有检查点 */
    List<CheckpointEntry> listByOwner(String ownerId);
}
