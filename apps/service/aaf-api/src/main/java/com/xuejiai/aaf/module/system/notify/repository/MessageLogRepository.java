package com.xuejiai.aaf.module.system.notify.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.notify.domain.MessageLog;

/** 统一消息发送日志数据访问层。 */
public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {}
