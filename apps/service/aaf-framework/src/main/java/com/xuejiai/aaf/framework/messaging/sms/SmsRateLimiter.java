package com.xuejiai.aaf.framework.messaging.sms;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;

import lombok.extern.slf4j.Slf4j;

/** 短信频率限制器，基于 Redis 计数。限流阈值从系统配置读取，支持运行时调整。 */
@Slf4j
@Component
public class SmsRateLimiter {

    private static final String KEY_PREFIX = "sms:rate:";

    /** 每分钟限制配置键，默认 1 */
    static final String CONFIG_MAX_PER_MINUTE = "sms.rate_limit.max_per_minute";

    /** 每小时限制配置键，默认 5 */
    static final String CONFIG_MAX_PER_HOUR = "sms.rate_limit.max_per_hour";

    private final StringRedisTemplate redisTemplate;
    private final SystemConfigService configService;

    public SmsRateLimiter(StringRedisTemplate redisTemplate, SystemConfigService configService) {
        this.redisTemplate = redisTemplate;
        this.configService = configService;
    }

    /** 检查是否允许发送，不允许则抛异常 */
    public void check(String phone) {
        int maxPerMinute = configService.getInteger(CONFIG_MAX_PER_MINUTE, 1);
        int maxPerHour = configService.getInteger(CONFIG_MAX_PER_HOUR, 5);

        var minuteKey = KEY_PREFIX + phone + ":min";
        var hourKey = KEY_PREFIX + phone + ":hour";

        // 每分钟限制
        var minuteCount = redisTemplate.opsForValue().get(minuteKey);
        if (minuteCount != null && Integer.parseInt(minuteCount) >= maxPerMinute) {
            throw new RuntimeException("短信发送过于频繁，请1分钟后重试");
        }

        // 每小时限制
        var hourCount = redisTemplate.opsForValue().get(hourKey);
        if (hourCount != null && Integer.parseInt(hourCount) >= maxPerHour) {
            throw new RuntimeException("短信发送次数已达上限，请1小时后重试");
        }

        // 计数递增
        redisTemplate.opsForValue().increment(minuteKey);
        redisTemplate.expire(minuteKey, Duration.ofMinutes(1));
        redisTemplate.opsForValue().increment(hourKey);
        redisTemplate.expire(hourKey, Duration.ofHours(1));
    }
}
