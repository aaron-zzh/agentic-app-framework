package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.HumanApproval;
import com.xuejiai.aaf.framework.intelligent.assistant.port.HumanApprovalPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

public class JpaHumanApprovalAdapter implements HumanApprovalPort {
    private final HumanApprovalRepository repository;

    public JpaHumanApprovalAdapter(HumanApprovalRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HumanApproval> find(TenantId tenantId, String approvalId) {
        return repository
                .findByTenantIdAndApprovalId(tenantId.value(), approvalId)
                .map(HumanApprovalEntity::getApproval);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HumanApproval> pending(TenantId tenantId, TaskId taskId) {
        return repository
                .findByTenantIdAndTaskIdAndStatus(
                        tenantId.value(), taskId.value(), HumanApproval.Status.PENDING.name())
                .stream()
                .map(HumanApprovalEntity::getApproval)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HumanApproval> pending(TenantId tenantId, UserId userId) {
        return repository
                .findByTenantIdAndStatus(tenantId.value(), HumanApproval.Status.PENDING.name())
                .stream()
                .map(HumanApprovalEntity::getApproval)
                .filter(approval -> approval.invocationContext().userId().equals(userId))
                .toList();
    }
}
