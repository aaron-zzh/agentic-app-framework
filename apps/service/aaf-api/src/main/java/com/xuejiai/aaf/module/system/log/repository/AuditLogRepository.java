package com.xuejiai.aaf.module.system.log.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.log.domain.AuditLog;

/** 审计日志仓储。 */
public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** 获取最新一条审计日志（用于链式哈希）。 */
    Optional<AuditLog> findTopByOrderByIdDesc();
}
