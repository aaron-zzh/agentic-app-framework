package com.xuejiai.aaf.module.system.log.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.log.domain.SmsLog;

public interface SmsLogRepository extends JpaRepository<SmsLog, Long> {}
