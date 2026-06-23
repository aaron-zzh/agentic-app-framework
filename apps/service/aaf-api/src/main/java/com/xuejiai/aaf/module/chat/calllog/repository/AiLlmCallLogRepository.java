package com.xuejiai.aaf.module.chat.calllog.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.chat.calllog.domain.AiLlmCallLog;

/** LLM 调用日志 Repository。 */
public interface AiLlmCallLogRepository extends JpaRepository<AiLlmCallLog, Long> {}
