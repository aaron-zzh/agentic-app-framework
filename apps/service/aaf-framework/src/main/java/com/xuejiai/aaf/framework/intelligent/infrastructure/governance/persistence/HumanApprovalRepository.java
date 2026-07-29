package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface HumanApprovalRepository extends JpaRepository<HumanApprovalEntity, String> {

    Optional<HumanApprovalEntity> findByTenantIdAndApprovalId(String tenantId, String approvalId);

    List<HumanApprovalEntity> findByTenantIdAndTaskIdAndStatus(
            String tenantId, String taskId, String status);

    List<HumanApprovalEntity> findByTenantIdAndStatus(String tenantId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM HumanApprovalEntity a WHERE a.tenantId = :tenantId AND a.approvalId = :approvalId")
    Optional<HumanApprovalEntity> findForUpdate(String tenantId, String approvalId);
}
