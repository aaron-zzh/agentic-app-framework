package com.xuejiai.aaf.module.channel.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.channel.domain.WebhookLog;

/** Webhook 推送日志数据访问层。 */
public interface WebhookLogRepository extends JpaRepository<WebhookLog, Long> {

    Page<WebhookLog> findByWebhookIdAndDeletedFalse(Long webhookId, Pageable pageable);

    /** 查询待重试的日志（状态为 failed 且未超过最大重试且到达重试时间） */
    List<WebhookLog> findByStatusAndNextRetryTimeBeforeAndDeletedFalse(
            String status, LocalDateTime now);
}
