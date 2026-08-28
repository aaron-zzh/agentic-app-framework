package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionProfileSnapshot;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/**
 * 单次执行画像的追加与恢复读取边界。
 *
 * <p>{@link #freeze} 是追加式写入：同一 execution 可多次调用，每次产生新记录，不做相同性校验。恢复取 {@link #find}
 * 返回的最新一条即可继续，不要求与执行开始时的画像相同——画像随物理调用推进（见 {@code ExecutionProfileSnapshot.PerCallProfile}）。
 */
public interface ExecutionProfileSnapshotPort {

    ExecutionProfileSnapshot freeze(ExecutionProfileSnapshot snapshot);

    /** 取该 execution 最新一条画像；未命中表示该执行尚未完整开始过一次物理调用。 */
    Optional<ExecutionProfileSnapshot> find(TenantId tenantId, ExecutionId executionId);
}
