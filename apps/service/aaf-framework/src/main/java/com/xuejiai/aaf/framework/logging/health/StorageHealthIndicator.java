package com.xuejiai.aaf.framework.logging.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.storage.StorageService;

/** 存储服务可用性健康检查。 */
@Component
public class StorageHealthIndicator implements HealthIndicator {

    private final StorageService storageService;

    public StorageHealthIndicator(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public Health health() {
        try {
            // 尝试获取一个不存在文件的 URL，验证存储服务连通性
            storageService.getUrl("health-check-probe");
            return Health.up().withDetail("type", storageService.getClass().getSimpleName()).build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("type", storageService.getClass().getSimpleName())
                    .build();
        }
    }
}
