package com.xuejiai.aaf.module.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.domain.OperationLogEntity;

/** 操作日志仓储。 */
public interface OperationLogRepository
        extends JpaRepository<OperationLogEntity, Long>, JpaSpecificationExecutor<OperationLogEntity> {}
