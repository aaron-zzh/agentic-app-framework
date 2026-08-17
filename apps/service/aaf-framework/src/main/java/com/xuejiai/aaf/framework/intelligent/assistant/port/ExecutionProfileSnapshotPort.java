package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionProfileSnapshot;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** 单次执行画像的冻结与恢复读取边界。 */
public interface ExecutionProfileSnapshotPort {

    ExecutionProfileSnapshot freeze(ExecutionProfileSnapshot snapshot);

    Optional<ExecutionProfileSnapshot> find(TenantId tenantId, ExecutionId executionId);
}
