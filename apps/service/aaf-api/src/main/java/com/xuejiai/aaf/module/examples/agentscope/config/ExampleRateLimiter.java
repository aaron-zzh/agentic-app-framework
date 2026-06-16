package com.xuejiai.aaf.module.examples.agentscope.config;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.ErrorCode;
import com.xuejiai.aaf.module.system.config.service.SystemConfigService;

import lombok.RequiredArgsConstructor;

/**
 * AgentScope 示例接口限流器。
 *
 * <p>基于 Redis 计数，每个 IP 每分钟最多调用 N 次。 限流阈值通过系统参数 {@code examples.agentscope_rate_limit_per_minute}
 * 动态控制（默认 20）。
 */
@Component
@RequiredArgsConstructor
public class ExampleRateLimiter {

    private static final String KEY_PREFIX = "example:rate:";

    private final StringRedisTemplate redisTemplate;
    private final SystemConfigService configService;

    /** 检查限流，超限抛异常。 */
    public void check(String ip) {
        int max =
                configService.getInteger(
                        SysConfigKeys.Examples.AGENTSCOPE_RATE_LIMIT_PER_MINUTE, 20);
        var key = KEY_PREFIX + ip;
        var count = redisTemplate.opsForValue().get(key);
        if (count != null && Integer.parseInt(count) >= max) {
            throw new BusinessException(
                    ErrorCode.of(429, "示例接口调用频繁，请 1 分钟后重试（限 " + max + " 次/分钟）"));
        }
        var newCount = redisTemplate.opsForValue().increment(key);
        if (newCount != null && newCount == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
    }
}
