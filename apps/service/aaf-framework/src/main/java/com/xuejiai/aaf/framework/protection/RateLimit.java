package com.xuejiai.aaf.framework.protection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解（基于 Redis 滑动计数）。
 *
 * <pre>
 * // 每个用户每分钟最多 20 次
 * {@literal @}RateLimit(limit = 20, windowSeconds = 60)
 * public SseEmitter run(...) { ... }
 * </pre>
 *
 * <p>限流 key 维度：{@code rate:{prefix}:{userId}}（需登录用户）或 {@code rate:{prefix}:{ip}}（匿名）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 时间窗口内允许的最大请求次数。 */
    int limit() default 20;

    /** 时间窗口长度，单位秒。 */
    int windowSeconds() default 60;

    /** 限流 key 前缀，默认取 "类名.方法名"。 */
    String prefix() default "";

    /** 超限提示信息。 */
    String message() default "请求过于频繁，请稍后再试";
}
