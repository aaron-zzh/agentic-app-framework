package com.xuejiai.aaf.module.system.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.workflow.domain.AutomationLog;

/**
 * 自动化执行日志仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface AutomationLogRepository
        extends JpaRepository<AutomationLog, Long>, JpaSpecificationExecutor<AutomationLog> {}
