package com.xuejiai.aaf.framework.task;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 分布式锁 AOP 切面。 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then "
                            + "return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class);

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(lock)")
    public Object around(ProceedingJoinPoint pjp, DistributedLock lock) throws Throwable {
        var key = "dlock:" + lock.key();
        var lockToken = UUID.randomUUID().toString();
        var acquired =
                Boolean.TRUE.equals(
                        redisTemplate
                                .opsForValue()
                                .setIfAbsent(key, lockToken, lock.ttlSeconds(), TimeUnit.SECONDS));
        if (!acquired) {
            log.debug("分布式锁获取失败，跳过执行: {}", key);
            return null;
        }
        try {
            return pjp.proceed();
        } finally {
            var released = redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(key), lockToken);
            if (!Long.valueOf(1L).equals(released)) {
                log.warn("分布式锁未释放，锁可能已过期或被其他节点重新获取: {}", key);
            }
        }
    }
}
