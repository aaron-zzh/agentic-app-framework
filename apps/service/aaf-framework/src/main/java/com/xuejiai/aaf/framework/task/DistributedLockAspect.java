package com.xuejiai.aaf.framework.task;

import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 分布式锁 AOP 切面。 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(lock)")
    public Object around(ProceedingJoinPoint pjp, DistributedLock lock) throws Throwable {
        var key = "dlock:" + lock.key();
        var acquired =
                Boolean.TRUE.equals(
                        redisTemplate
                                .opsForValue()
                                .setIfAbsent(key, "1", lock.ttlSeconds(), TimeUnit.SECONDS));
        if (!acquired) {
            log.debug("分布式锁获取失败，跳过执行: {}", key);
            return null;
        }
        try {
            return pjp.proceed();
        } finally {
            redisTemplate.delete(key);
        }
    }
}
