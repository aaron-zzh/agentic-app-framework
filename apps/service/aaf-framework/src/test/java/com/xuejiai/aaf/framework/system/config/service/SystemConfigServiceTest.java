package com.xuejiai.aaf.framework.system.config.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.xuejiai.aaf.framework.system.config.repository.SystemConfigRepository;

class SystemConfigServiceTest {

    @Test
    @DisplayName("Given 活跃事务 When 请求淘汰配置缓存 Then 仅在提交后删除 Redis key")
    void should_evict_cache_only_after_commit() {
        var redisTemplate = mock(StringRedisTemplate.class);
        var service =
                new SystemConfigService(mock(SystemConfigRepository.class), redisTemplate);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.evictAfterCommit("knowledge.extraction.system_prompt");

            verify(redisTemplate, never())
                    .delete("sys:config:knowledge.extraction.system_prompt");
            var synchronizations = TransactionSynchronizationManager.getSynchronizations();
            for (var synchronization : synchronizations) {
                synchronization.afterCommit();
            }
            verify(redisTemplate).delete("sys:config:knowledge.extraction.system_prompt");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
