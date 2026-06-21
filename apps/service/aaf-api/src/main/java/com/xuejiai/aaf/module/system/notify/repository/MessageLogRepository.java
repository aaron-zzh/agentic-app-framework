package com.xuejiai.aaf.module.system.notify.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.notify.domain.MessageLog;

/** 统一消息发送日志数据访问层。 */
public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {

    /** 按渠道分页查询未删除日志（如 channel="SMS"）。 */
    Page<MessageLog> findByChannelAndDeletedFalse(String channel, Pageable pageable);
}
