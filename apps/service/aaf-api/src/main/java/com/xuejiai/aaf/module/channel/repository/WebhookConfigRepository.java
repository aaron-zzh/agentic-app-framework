package com.xuejiai.aaf.module.channel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.channel.domain.WebhookConfig;

/**
 * Webhook 配置数据访问层。
 */
public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, Long> {

    List<WebhookConfig> findByStatusAndDeletedFalse(String status);

    List<WebhookConfig> findByDirectionAndStatusAndDeletedFalse(String direction, String status);
}
