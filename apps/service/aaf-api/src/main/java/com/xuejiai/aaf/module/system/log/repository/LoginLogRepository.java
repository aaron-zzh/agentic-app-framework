package com.xuejiai.aaf.module.system.log.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.log.domain.LoginLog;

/**
 * 登录日志仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface LoginLogRepository
        extends JpaRepository<LoginLog, Long>, JpaSpecificationExecutor<LoginLog> {}
