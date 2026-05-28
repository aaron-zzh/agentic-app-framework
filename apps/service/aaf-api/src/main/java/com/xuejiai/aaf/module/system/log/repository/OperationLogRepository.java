package com.xuejiai.aaf.module.system.log.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.log.domain.OperationLogEntity;

/**
 * 操作日志仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface OperationLogRepository
        extends JpaRepository<OperationLogEntity, Long>,
                JpaSpecificationExecutor<OperationLogEntity> {}
