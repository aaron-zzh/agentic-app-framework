package com.xuejiai.aaf.module.system.sms.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.sms.domain.SmsLog;

public interface SmsLogRepository extends JpaRepository<SmsLog, Long> {}
