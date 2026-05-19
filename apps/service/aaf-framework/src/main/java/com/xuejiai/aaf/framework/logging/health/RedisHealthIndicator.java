package com.xuejiai.aaf.framework.logging.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/** Redis 连通性健康检查。 */
@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (var connection = connectionFactory.getConnection()) {
            var pong = connection.commands().ping();
            if ("PONG".equals(pong)) {
                return Health.up().withDetail("ping", "PONG").build();
            }
            return Health.down().withDetail("ping", pong).build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
