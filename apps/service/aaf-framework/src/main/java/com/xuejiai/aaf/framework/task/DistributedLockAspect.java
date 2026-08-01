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

/**
 * 分布式锁 AOP 切面。
 *
 * <p>m32：获取不到锁时的语义此前是"返回 null 静默跳过"——用在有返回值的方法上，调用方无法区分 "本次被跳过"与"业务确实返回 null"。现在按返回类型区分：
 *
 * <ul>
 *   <li>void 方法（定时任务这类"抢到才跑"的场景）→ 跳过并记日志，保持原语义
 *   <li>有返回值的方法 → 抛 {@link LockNotAcquiredException}，强制调用方显式处理
 * </ul>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    /** m32：未取得分布式锁——有返回值的方法不再以 null 表示"跳过"。 */
    public static class LockNotAcquiredException extends RuntimeException {
        public LockNotAcquiredException(String key) {
            super("未取得分布式锁，本次执行已放弃: " + key);
        }
    }

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
            // m32：void 方法沿用"跳过"语义；有返回值的方法必须显式失败，否则 null 会被当成业务结果
            var returnType =
                    ((org.aspectj.lang.reflect.MethodSignature) pjp.getSignature()).getReturnType();
            if (returnType == void.class || returnType == Void.class) {
                log.debug("分布式锁获取失败，跳过执行: {}", key);
                return null;
            }
            log.info("分布式锁获取失败，拒绝执行有返回值方法: {}", key);
            throw new LockNotAcquiredException(key);
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
