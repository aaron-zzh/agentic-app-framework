package com.xuejiai.aaf.framework.messaging.sms;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 短信频率限制器，基于 Redis 计数。 */
@Slf4j
@Component
public class SmsRateLimiter {

    private static final String KEY_PREFIX = "sms:rate:";
    private static final int MAX_PER_MINUTE = 1;
    private static final int MAX_PER_HOUR = 5;

    private final StringRedisTemplate redisTemplate;

    public SmsRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 检查是否允许发送，不允许则抛异常 */
    public void check(String phone) {
        var minuteKey = KEY_PREFIX + phone + ":min";
        var hourKey = KEY_PREFIX + phone + ":hour";

        // 每分钟限制
        var minuteCount = redisTemplate.opsForValue().get(minuteKey);
        if (minuteCount != null && Integer.parseInt(minuteCount) >= MAX_PER_MINUTE) {
            throw new RuntimeException("短信发送过于频繁，请1分钟后重试");
        }

        // 每小时限制
        var hourCount = redisTemplate.opsForValue().get(hourKey);
        if (hourCount != null && Integer.parseInt(hourCount) >= MAX_PER_HOUR) {
            throw new RuntimeException("短信发送次数已达上限，请1小时后重试");
        }

        // 计数递增
        redisTemplate.opsForValue().increment(minuteKey);
        redisTemplate.expire(minuteKey, Duration.ofMinutes(1));
        redisTemplate.opsForValue().increment(hourKey);
        redisTemplate.expire(hourKey, Duration.ofHours(1));
    }
}
