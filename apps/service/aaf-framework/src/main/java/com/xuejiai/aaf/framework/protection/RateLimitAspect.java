package com.xuejiai.aaf.framework.protection;

import java.time.Duration;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** {@link RateLimit} 注解的 AOP 拦截器，基于 Redis 计数实现滑动窗口限流。 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String KEY_PREFIX = "rate:";

    private final StringRedisTemplate redisTemplate;
    private final OperatorContext operatorContext;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        var key = buildKey(pjp, rateLimit);
        var count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            // 首次写入，设置过期时间
            redisTemplate.expire(key, Duration.ofSeconds(rateLimit.windowSeconds()));
        }
        if (count > rateLimit.limit()) {
            log.warn("[限流] key={} count={} limit={}", key, count, rateLimit.limit());
            throw new RateLimitExceededException(rateLimit.message());
        }
        return pjp.proceed();
    }

    private String buildKey(ProceedingJoinPoint pjp, RateLimit rateLimit) {
        var prefix = rateLimit.prefix();
        if (prefix.isEmpty()) {
            var sig = (MethodSignature) pjp.getSignature();
            prefix = sig.getDeclaringType().getSimpleName() + "." + sig.getMethod().getName();
        }
        // 优先用登录用户 ID，匿名则用 IP
        var identity =
                operatorContext.currentUserId().map(Object::toString).orElseGet(this::currentIp);
        return KEY_PREFIX + prefix + ":" + identity;
    }

    private String currentIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            var request = attrs.getRequest();
            var ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
            // X-Forwarded-For 可能携带多个 IP，取第一个
            return ip.split(",")[0].trim();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
