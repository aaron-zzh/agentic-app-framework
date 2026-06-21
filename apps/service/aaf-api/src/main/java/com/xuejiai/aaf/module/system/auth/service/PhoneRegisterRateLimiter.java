package com.xuejiai.aaf.module.system.auth.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.AUTH_REGISTER_IP_RATE_LIMIT;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 手机号自动注册的 IP 风控（固定窗口计数器）。
 *
 * <p>规则：同 IP 1 小时内"登录即注册"分支创建用户不超过 {@link #MAX_REGISTER_PER_HOUR} 次，超出抛 {@link
 * com.xuejiai.aaf.module.system.ErrorCodeConstants#AUTH_REGISTER_IP_RATE_LIMIT}。
 *
 * <p>计数策略：
 *
 * <ul>
 *   <li>{@link #checkBeforeRegister(String)} 仅校验当前计数是否超阈值，<b>不递增</b>，避免风控本身触发计数膨胀。
 *   <li>{@link #recordRegister(String)} 在自动注册成功后调用，原子 INCR 并对首次写入设置 1 小时过期。
 * </ul>
 *
 * <p>TODO 阈值后续接入 sys_config 动态配置（key: {@code security.phone_register_ip_hour_limit}）。
 *
 * @author AaronZZH &amp; Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhoneRegisterRateLimiter {

    private static final String KEY_PREFIX = "phone_register_ip_limit:";
    private static final Duration WINDOW = Duration.ofHours(1);

    /** 阈值：同 IP 1 小时内自动注册次数上限 */
    static final int MAX_REGISTER_PER_HOUR = 10;

    private final StringRedisTemplate redisTemplate;

    /**
     * 注册前校验。超阈值抛 {@link
     * com.xuejiai.aaf.module.system.ErrorCodeConstants#AUTH_REGISTER_IP_RATE_LIMIT}。
     */
    public void checkBeforeRegister(String ip) {
        if (ip == null || ip.isBlank()) return; // IP 缺失时不做风控
        String key = KEY_PREFIX + ip;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return;
        try {
            int count = Integer.parseInt(value);
            if (count >= MAX_REGISTER_PER_HOUR) {
                log.warn("【注册风控】IP={} 1 小时内已注册 {} 次，触发限流", ip, count);
                throw exception(AUTH_REGISTER_IP_RATE_LIMIT);
            }
        } catch (NumberFormatException e) {
            log.warn("【注册风控】IP={} 计数器值非法，重置: {}", ip, value);
            redisTemplate.delete(key);
        }
    }

    /** 注册成功后递增计数器；首次写入时设置 1 小时窗口过期。 */
    public void recordRegister(String ip) {
        if (ip == null || ip.isBlank()) return;
        String key = KEY_PREFIX + ip;
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
    }
}
