package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface ToolInvocationReceiptRepository
        extends JpaRepository<ToolInvocationReceiptEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ToolInvocationReceiptEntity> findByReceiptKey(String receiptKey);
}
