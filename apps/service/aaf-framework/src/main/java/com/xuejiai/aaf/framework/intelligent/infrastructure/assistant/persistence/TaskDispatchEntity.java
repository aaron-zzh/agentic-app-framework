package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "ai_task_dispatch",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"org_id", "dispatch_id"})})
public class TaskDispatchEntity extends AssistantRuntimeEntity {

    @Column(name = "dispatch_id", nullable = false, length = 128)
    private String dispatchId;

    @Column(name = "execution_id", nullable = false, length = 128)
    private String executionId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(nullable = false)
    private Long generation;

    @Column(name = "fencing_token", nullable = false)
    private Long fencingToken;

    @Column(name = "delivery_attempts", nullable = false)
    private Integer deliveryAttempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "runtime_lock_version", nullable = false)
    private Long runtimeLockVersion;

    public com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDispatch getDispatch() {
        return new com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDispatch(
                dispatchId,
                new com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId(
                        getTenantId()),
                new com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId(
                        executionId),
                com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDispatch.Status.valueOf(
                        status),
                nextRunAt,
                leaseOwner,
                leaseUntil,
                generation,
                fencingToken,
                deliveryAttempts,
                lastError,
                runtimeLockVersion == null ? 0 : runtimeLockVersion,
                createdAt,
                updatedAt);
    }

    public void setDispatch(
            com.xuejiai.aaf.framework.intelligent.assistant.model.TaskDispatch dispatch) {
        dispatchId = dispatch.dispatchId();
        setTenantId(dispatch.tenantId().value());
        executionId = dispatch.executionId().value();
        status = dispatch.status().name();
        nextRunAt = dispatch.nextRunAt();
        leaseOwner = dispatch.leaseOwner();
        leaseUntil = dispatch.leaseUntil();
        generation = dispatch.generation();
        fencingToken = dispatch.fencingToken();
        deliveryAttempts = dispatch.deliveryAttempts();
        lastError = dispatch.lastError();
        createdAt = dispatch.createdAt();
        updatedAt = dispatch.updatedAt();
    }
}
