package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 持久审批状态边界。 */
public interface HumanApprovalPort {

    Optional<HumanApproval> find(TenantId tenantId, String approvalId);

    List<HumanApproval> pending(TenantId tenantId, TaskId taskId);

    List<HumanApproval> pending(TenantId tenantId, UserId userId);
}
