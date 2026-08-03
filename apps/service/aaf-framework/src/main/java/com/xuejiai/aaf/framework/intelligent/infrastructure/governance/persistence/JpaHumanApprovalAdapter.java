package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.time.Instant;
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
    @Transactional
    public HumanApproval create(HumanApproval approval) {
        if (repository.existsById(approval.approvalId())) {
            throw new IllegalStateException("approvalId 已存在: " + approval.approvalId());
        }
        var entity = new HumanApprovalEntity();
        apply(entity, approval);
        return repository.saveAndFlush(entity).getApproval();
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

    @Override
    @Transactional
    public DecisionResult decide(
            TenantId tenantId,
            String approvalId,
            HumanApproval.Status decision,
            String decidedBy,
            String reason,
            Instant at) {
        var entity =
                repository
                        .findForUpdate(tenantId.value(), approvalId)
                        .orElseThrow(() -> new IllegalArgumentException("审批不存在: " + approvalId));
        var current = entity.getApproval();
        if (current.status() != HumanApproval.Status.PENDING) {
            requireSameDecision(current, decision, decidedBy, reason);
            return new DecisionResult(current, false);
        }
        var decided =
                new HumanApproval(
                        current.approvalId(),
                        current.invocationContext(),
                        current.action(),
                        current.resource(),
                        current.reason(),
                        current.impact(),
                        current.dataUsage(),
                        current.remediation(),
                        current.requestedConditions(),
                        current.reversible(),
                        decision,
                        current.createdAt(),
                        at,
                        decidedBy,
                        reason);
        apply(entity, decided);
        return new DecisionResult(repository.saveAndFlush(entity).getApproval(), true);
    }

    private static void requireSameDecision(
            HumanApproval existing,
            HumanApproval.Status decision,
            String decidedBy,
            String reason) {
        if (existing.status() != decision
                || !Objects.equals(existing.decidedBy(), decidedBy)
                || !Objects.equals(existing.decisionReason(), reason)) {
            throw new IllegalStateException("approvalId 已绑定不同决定事实");
        }
    }

    private static void apply(HumanApprovalEntity entity, HumanApproval approval) {
        entity.setApprovalId(approval.approvalId());
        entity.setTenantId(approval.invocationContext().tenantId().value());
        entity.setTaskId(approval.invocationContext().taskId().value());
        entity.setStatus(approval.status().name());
        entity.setCreatedAt(approval.createdAt());
        entity.setDecidedAt(approval.decidedAt());
        entity.setApproval(approval);
    }
}
