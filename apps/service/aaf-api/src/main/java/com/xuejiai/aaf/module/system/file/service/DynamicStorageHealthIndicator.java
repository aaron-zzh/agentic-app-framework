package com.xuejiai.aaf.module.system.file.service;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 动态主存储配置健康检查。 */
@Component("storageHealthIndicator")
@RequiredArgsConstructor
public class DynamicStorageHealthIndicator implements HealthIndicator {

    private final StorageRouter storageRouter;

    @Override
    public Health health() {
        try {
            var storage = storageRouter.currentMaster();
            return Health.up()
                    .withDetail("configId", storage.storageConfigId())
                    .withDetail("type", storage.storageType().name())
                    .build();
        } catch (RuntimeException failure) {
            return Health.down(failure).build();
        }
    }
}
