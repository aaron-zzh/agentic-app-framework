package com.xuejiai.aaf.module.chat.calllog.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.chat.calllog.domain.AiToolCallLog;

/** 工具调用日志 Repository。 */
public interface AiToolCallLogRepository extends JpaRepository<AiToolCallLog, Long> {}
